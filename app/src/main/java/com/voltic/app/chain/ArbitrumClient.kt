package com.voltic.app.chain

import android.util.Log
import com.voltic.contracts.VolticSmartWallet
import io.ethers.abi.eip712.EIP712Domain
import io.ethers.abi.eip712.EIP712Field
import io.ethers.abi.eip712.EIP712TypedData
import io.ethers.core.FastHex
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.core.types.transaction.TxLegacy
import io.ethers.core.utils.EthUnit
import io.ethers.providers.Provider
import io.ethers.signers.Signer
import io.github.artificialpb.bignum.BigInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.SocketTimeoutException

class ArbitrumClient {

    companion object {
        private val config = ChainConfig.current
        val ARBITRUM_CHAIN_ID = config.chainId
        val ARBITRUM_CHAIN_NAME = config.chainName
        val EXPLORER_URL = config.explorerUrl
        val VAULT_ADDRESS = config.vaultAddress // kept as String — unchanged public surface
        private val VAULT_ADDRESS_TYPED = Address(VAULT_ADDRESS)

        // One Provider per URL, built once and reused — same role the web3jCache
        // played before: avoid throwing away warm connections on every call.
        private val providerCache = mutableMapOf<String, Provider>()
        private val cacheLock = Mutex()

        private suspend fun getOrBuildProvider(url: String): Provider = cacheLock.withLock {
            providerCache.getOrPut(url) {
                // Chain ID is already known from ChainConfig, so this overload doesn't
                // cost an extra eth_chainId round trip the way a bare build() would.
                Provider.builder(url).build(ARBITRUM_CHAIN_ID).unwrap()
            }
        }

        // Index mutation happens under this so concurrent calls (e.g. dashboard
        // firing off balance + spend-limit + nonce at once) can't race each other.
        private val indexLock = Mutex()
        private var currentArbRpcIndex = 0

        fun formatError(e: Throwable): String {
            val message = e.message ?: "Unknown error"

            // 1. Try to decode as a Vault custom error or standard revert from the message
            VaultErrorDecoder.decode(message)?.let { return it }

            // NOTE: the old TransactionException-based revert-reason lookup (step 2 in
            // the web3j version) doesn't apply here — ethers-kt contract calls decode
            // reverts (including custom Solidity errors) into ContractError up front,
            // so that information is already folded into `message` by the time an
            // exception reaches here.

            // 2. Fallback to existing manual patterns or the raw message
            val msg = message.lowercase()
            return if (msg.contains("0x0") && msg.contains("revert")) {
                "Sender has reached maximum spending limit or has no funds."
            } else {
                message
            }
        }

        val txMutex = Mutex()
    }

    private suspend fun <T> runWithFallback(
        rpcs: List<String>,
        indexPointer: kotlin.reflect.KMutableProperty0<Int>,
        block: suspend (Provider) -> T
    ): T = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        val startIndex = indexLock.withLock { indexPointer.get() }

        for (attempt in rpcs.indices) {
            val currentIndex = (startIndex + attempt) % rpcs.size
            val url = rpcs[currentIndex]
            val provider = getOrBuildProvider(url)

            try {
                val result = block(provider)
                indexLock.withLock { indexPointer.set(currentIndex) }
                return@withContext result
            } catch (e: Exception) {
                lastException = e

                // ethers-kt RPC calls return Result<T, RpcError> instead of throwing —
                // but every call site below uses .unwrap(), which converts a failed
                // Result back into a thrown exception so this loop can stay
                // exception-based, same shape as the old web3j version. Network-layer
                // failures (timeouts, connection refused) surface as the *cause* of
                // that wrapped exception rather than as `e` itself.
                val cause = e.cause
                val isKnownFlaky = cause is ConnectException ||
                        cause is SocketTimeoutException ||
                        e.message?.contains("521") == true ||
                        e.message?.contains("429") == true ||
                        e.message?.contains("sync status") == true

                if (isKnownFlaky) {
                    Log.w("ArbitrumClient", "RPC failed ($url): ${e.message}, trying next")
                    continue
                } else {
                    throw e
                }
            }
        }
        throw lastException ?: Exception("All RPCs failed")
    }

    private suspend fun <T> runArb(block: suspend (Provider) -> T): T =
        runWithFallback(config.arbitrumRpcs, Companion::currentArbRpcIndex, block)


    // --- Clean Data Class for NFC prep ---
    // Kept as BigInteger (unchanged) so NfcReaderManager / VolticHceService's string
    // interpolation and BigInteger(parts[n]) parsing don't need to change.
    data class OfflinePaymentParams(
        val vaultNonce: BigInteger,
        val eoaNonce: BigInteger,
        val gasPrice: BigInteger,
        val gasLimit: BigInteger
    )

    // ==========================================
    // DYNAMIC GAS ESTIMATOR
    // ==========================================
    private suspend fun estimateGasLimit(
        provider: Provider,
        from: Address,
        to: Address,
        value: BigInteger,
        data: Bytes? = null,
        fallback: Long
    ): Long {
        return try {
            val call = CallRequest().also {
                it.from = from
                it.to = to
                it.value = value
                it.data = data
            }
            val estimate = provider.estimateGas(call, BlockId.LATEST).sendAwait().unwrap()
            // 20% buffer
            (estimate * 12) / 10
        } catch (e: Exception) {
            Log.e("ArbitrumClient", "Gas estimation failed, using fallback", e)
            fallback
        }
    }

    private suspend fun getBufferedGasPrice(provider: Provider): BigInteger {
        return try {
            val baseGasPrice = provider.getGasPrice().sendAwait().unwrap()
            // 20% Gas Price buffer (1.2x base price)
            baseGasPrice.multiply(BigInteger.valueOf(12)).divide(BigInteger.valueOf(10))
        } catch (e: Exception) {
            Log.e("ArbitrumClient", "Gas price fetch failed, using fallback", e)
            // Safe fallback to 0.1 Gwei if RPC node fails to return gas price
            BigInteger.valueOf(100_000_000)
        }
    }

    suspend fun getBalance(address: String): BigInteger = runArb { provider ->
        provider.getBalance(Address(address), BlockId.LATEST).sendAwait().unwrap()
    }

    suspend fun getVaultBalance(address: String): BigInteger = runArb { provider ->
        val vault = VolticSmartWallet(provider, VAULT_ADDRESS_TYPED)
        vault.balanceOf(Address(address)).call(BlockId.LATEST).sendAwait().unwrap()
    }

    suspend fun getVaultNonce(address: String): BigInteger = runArb { provider ->
        val vault = VolticSmartWallet(provider, VAULT_ADDRESS_TYPED)
        vault.nonces(Address(address)).call(BlockId.LATEST).sendAwait().unwrap()
    }

    // Delegates to the standalone EnsResolver (same package, not yet touched by this
    // migration) — it pulls config.ethereumRpcs itself and throws EnsResolutionException
    // on any failure (invalid name, no resolver, unregistered). Callers further up
    // (send-flow UI) should catch EnsResolutionException specifically to show a clean
    // message instead of a generic error.
    suspend fun getReceiverAddress(rawRecipient: String): String =
        EnsResolver.getReceiverAddress(rawRecipient)

    suspend fun getOfflinePaymentParams(
        customerAddress: String,
        toAddress: String,
        amountEth: String
    ): OfflinePaymentParams = runArb { provider ->
        val vaultNonce = getVaultNonce(customerAddress)
        val eoaNonce = provider.getTransactionCount(Address(customerAddress), BlockId.PENDING).sendAwait().unwrap()
        val gasPrice = getBufferedGasPrice(provider)

        val amountWei = EthUnit.ETHER.toWei(amountEth.ifBlank { "0" }).toBigInteger()
        val resolvedTo = getReceiverAddress(toAddress)

        // Only estimate for Legacy transfer here. Vault is estimated during broadcast when we have the signature.
        val gasLimit = estimateGasLimit(
            provider = provider,
            from = Address(customerAddress),
            to = Address(resolvedTo),
            value = amountWei,
            data = null,
            fallback = 21_000L
        )

        OfflinePaymentParams(vaultNonce, BigInteger.valueOf(eoaNonce), gasPrice, BigInteger.valueOf(gasLimit))
    }

    suspend fun broadcastLegacyTransaction(signedTxHex: String): String = runArb { provider ->
        txMutex.withLock {
            val pending = provider.sendRawTransaction(FastHex.decode(signedTxHex)).sendAwait().unwrap()
            pending.hash.toString()
        }
    }

    suspend fun broadcastNfcVaultPayment(
        merchantCredentials: Signer,
        customerAddress: String,
        toAddress: String,
        amountEth: String,
        nonce: BigInteger,
        deadline: BigInteger,
        signatureHex: String
    ): String = runArb { provider ->
        txMutex.withLock {
            val amountWei = EthUnit.ETHER.toWei(amountEth).toBigInteger()
            val resolvedTo = getReceiverAddress(toAddress)
            val vault = VolticSmartWallet(provider, VAULT_ADDRESS_TYPED)

            // 1. Build the call with the REAL signature so the contract won't revert during estimation
            val call = vault.executePayment(
                Address(customerAddress),
                Address(resolvedTo),
                amountWei,
                nonce,
                deadline,
                Bytes(signatureHex)
            )

            // 2. Fetch the true gas limit, safe fallback of 150_000 for storage updates
            val trueGasLimit = estimateGasLimit(
                provider = provider,
                from = merchantCredentials.address,
                to = VAULT_ADDRESS_TYPED,
                value = BigInteger.ZERO,
                data = call.data,
                fallback = 150_000L
            )
            call.gas(trueGasLimit)
            call.gasPrice(getBufferedGasPrice(provider))

            // 3. Sign + broadcast, then wait for the receipt so we can check status
            val pending = call.send(merchantCredentials).sendAwait().unwrap()
            val receipt = pending.inclusion().unwrap()

            require(receipt.isSuccessful) { "Transaction reverted by Vault" }
            receipt.transactionHash.toString()
        }
    }

    suspend fun depositToVault(credentials: Signer, amountEth: String): String = runArb { provider ->
        txMutex.withLock {
            val amountWei = EthUnit.ETHER.toWei(amountEth).toBigInteger()
            val vault = VolticSmartWallet(provider, VAULT_ADDRESS_TYPED)
            val call = vault.deposit().value(amountWei)

            val dynamicGasLimit = estimateGasLimit(provider, credentials.address, VAULT_ADDRESS_TYPED, amountWei, call.data, 60_000L)
            call.gas(dynamicGasLimit)
            call.gasPrice(getBufferedGasPrice(provider))

            val pending = call.send(credentials).sendAwait().unwrap()
            val receipt = pending.inclusion().unwrap()
            require(receipt.isSuccessful) { "Vault deposit failed" }
            receipt.transactionHash.toString()
        }
    }

    suspend fun withdrawFromVault(credentials: Signer, amountEth: String): String = runArb { provider ->
        txMutex.withLock {
            val amountWei = EthUnit.ETHER.toWei(amountEth).toBigInteger()
            val vault = VolticSmartWallet(provider, VAULT_ADDRESS_TYPED)
            val call = vault.withdraw(amountWei)

            val dynamicGasLimit = estimateGasLimit(provider, credentials.address, VAULT_ADDRESS_TYPED, BigInteger.ZERO, call.data, 70_000L)
            call.gas(dynamicGasLimit)
            call.gasPrice(getBufferedGasPrice(provider))

            val pending = call.send(credentials).sendAwait().unwrap()
            val receipt = pending.inclusion().unwrap()
            require(receipt.isSuccessful) { "Vault withdrawal failed" }
            receipt.transactionHash.toString()
        }
    }

    data class SpendLimitInfo(val amount: BigInteger, val spent: BigInteger, val period: Int)

    suspend fun getSpendLimitInfo(address: String): SpendLimitInfo = runArb { provider ->
        val vault = VolticSmartWallet(provider, VAULT_ADDRESS_TYPED)
        val limits = vault.spendLimits(Address(address)).call(BlockId.LATEST).sendAwait().unwrap()
        val period = vault.spendPeriod(Address(address)).call(BlockId.LATEST).sendAwait().unwrap()
        SpendLimitInfo(limits.amount, limits.spentInPeriod, period.toInt())
    }

    // NOTE: periodIndex used to be wrapped in a (misencoded, per the ABI — the contract
    // declares `LimitPeriod period` i.e. uint8, not uint256) Uint256 by the web3j version.
    // The real generated binding takes `period: BigInteger` matching the actual uint8 ABI
    // slot, so this now encodes correctly.
    suspend fun updateSpendLimit(credentials: Signer, periodIndex: Int, amountEth: String): String = runArb { provider ->
        txMutex.withLock {
            val amountWei = EthUnit.ETHER.toWei(amountEth).toBigInteger()
            val vault = VolticSmartWallet(provider, VAULT_ADDRESS_TYPED)
            val call = vault.setSpendLimit(BigInteger.valueOf(periodIndex.toLong()), amountWei)

            val dynamicGasLimit = estimateGasLimit(provider, credentials.address, VAULT_ADDRESS_TYPED, BigInteger.ZERO, call.data, 80_000L)
            call.gas(dynamicGasLimit)
            call.gasPrice(getBufferedGasPrice(provider))

            val pending = call.send(credentials).sendAwait().unwrap()
            val receipt = pending.inclusion().unwrap()
            require(receipt.isSuccessful) { "Failed to update spending limit" }
            receipt.transactionHash.toString()
        }
    }

    suspend fun sendEth(
        credentials: Signer,
        toAddress: String,
        amountEth: String
    ): String = runArb { provider ->
        txMutex.withLock {
            val resolvedAddress = getReceiverAddress(toAddress)
            val fromAddress = credentials.address
            val amountWei = EthUnit.ETHER.toWei(amountEth).toBigInteger()

            val gasLimit = estimateGasLimit(provider, fromAddress, Address(resolvedAddress), amountWei, null, 21_000L)
            val nonce = provider.getTransactionCount(fromAddress, BlockId.PENDING).sendAwait().unwrap()
            val gasPrice = getBufferedGasPrice(provider)

            val rawTransaction = TxLegacy(
                to = Address(resolvedAddress),
                value = amountWei,
                nonce = nonce,
                gas = gasLimit,
                gasPrice = gasPrice,
                data = null,
                chainId = ARBITRUM_CHAIN_ID
            )
            val signedTx = credentials.signTransaction(rawTransaction)

            val pending = provider.sendRawTransaction(signedTx).sendAwait().unwrap()
            pending.hash.toString()
        }
    }

    suspend fun executeVaultPayment(
        credentials: Signer,
        toAddress: String,
        amountEth: String
    ): String = withContext(Dispatchers.IO) {
        val resolvedTo = getReceiverAddress(toAddress)
        val ownerAddress = credentials.address.toString()

        val nonce = getVaultNonce(ownerAddress)
        val deadline = BigInteger.valueOf(System.currentTimeMillis() / 1000 + 1800)
        val signatureHex = signVaultPayment(credentials, resolvedTo, amountEth, nonce, deadline)

        // Forward directly to the clean broadcast helper!
        broadcastNfcVaultPayment(
            merchantCredentials = credentials,
            customerAddress = ownerAddress,
            toAddress = resolvedTo,
            amountEth = amountEth,
            nonce = nonce,
            deadline = deadline,
            signatureHex = signatureHex
        )
    }

    /**
     * Signs the "Payment" EIP-712 struct, replicating VolticSmartWallet.sol's domain
     * (name="VolticSmartWallet", version="1", no salt — the OpenZeppelin EIP712 default)
     * and its inline PAYMENT_TYPEHASH exactly. ethers-kt computes the domain separator
     * and struct hash itself — no more manual keccak/ABI-encode tower.
     */
    fun signVaultPayment(
        signer: Signer,
        to: String,
        amountEth: String,
        nonce: BigInteger,
        deadline: BigInteger
    ): String {
        val amountWei = EthUnit.ETHER.toWei(amountEth).toBigInteger()

        val domain = EIP712Domain(
            name = "VolticSmartWallet",
            version = "1",
            chainId = BigInteger.valueOf(ARBITRUM_CHAIN_ID),
            verifyingContract = VAULT_ADDRESS_TYPED
        )

        val typedData = EIP712TypedData(
            primaryType = "Payment",
            types = mapOf(
                "Payment" to listOf(
                    EIP712Field("owner", "address"),
                    EIP712Field("to", "address"),
                    EIP712Field("amount", "uint256"),
                    EIP712Field("nonce", "uint256"),
                    EIP712Field("deadline", "uint256"),
                )
            ),
            // NOTE: for this string-keyed (non-ContractStruct) EIP712TypedData form,
            // ethers-kt's codec expects every value as a String regardless of field
            // type ("address" fields included) — it parses them back internally.
            message = mapOf(
                "owner" to signer.address.toString(),
                "to" to to,
                "amount" to amountWei.toString(),
                "nonce" to nonce.toString(),
                "deadline" to deadline.toString(),
            ),
            domain = domain
        )

        // v is already Electrum-offset (27/28) — same convention web3j's Sign.signMessage
        // used, and what OpenZeppelin's ECDSA.recover expects in the 65-byte signature.
        val signature = typedData.sign(signer)
        return FastHex.encodeWithPrefix(signature.toByteArray())
    }

    fun signEthTransactionOffline(
        credentials: Signer,
        toAddress: String,
        amountEth: String,
        nonce: BigInteger,
        gasPriceWei: BigInteger,
        gasLimit: BigInteger
    ): ByteArray {
        val amountWei = EthUnit.ETHER.toWei(amountEth).toBigInteger()
        val rawTransaction = TxLegacy(
            to = Address(toAddress),
            value = amountWei,
            nonce = nonce.toLong(),
            gas = gasLimit.toLong(),
            gasPrice = gasPriceWei,
            data = null,
            chainId = ARBITRUM_CHAIN_ID
        )
        // .toRlp() gives the raw RLP-encoded signed tx bytes — same thing
        // TransactionEncoder.signMessage(...) used to hand back directly.
        return credentials.signTransaction(rawTransaction).toRlp()
    }
}