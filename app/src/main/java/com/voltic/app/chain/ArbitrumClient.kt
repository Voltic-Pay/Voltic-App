package com.voltic.app.chain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.voltic.contracts.VolticSmartWallet
import io.github.adraffy.ens.ENSNormalize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.crypto.*
import org.web3j.ens.EnsResolver
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.ReadonlyTransactionManager
import org.web3j.tx.gas.StaticGasProvider
import org.web3j.utils.Convert
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger

class ArbitrumClient {

    companion object {
        const val ARBITRUM_RPC_URL = "https://sepolia-rollup.arbitrum.io/rpc"
        const val ARBITRUM_CHAIN_ID = 421614L
        const val ENS_RPC_URL = "https://ethereum-sepolia-rpc.publicnode.com"

        // Deployed Vault Address on Arbitrum Sepolia
        const val VAULT_ADDRESS = "0x2EB9cD3C24C7cA7F7Eb7e563Be14C7Dd60504B6e"

        val web3j: Web3j by lazy { Web3j.build(HttpService(ARBITRUM_RPC_URL)) }
        val ensWeb3j: Web3j by lazy { Web3j.build(HttpService(ENS_RPC_URL)) }
        val txMutex = Mutex()

        private val readOnlyGasProvider = StaticGasProvider(BigInteger.ZERO, BigInteger.valueOf(300_000))

        fun formatError(message: String?): String {
            val msg = message ?: return "Unknown error"
            return if (msg.contains("0x0") && msg.contains("revert", ignoreCase = true)) {
                "sender have reched maxiumim spending limit or have no funds"
            } else {
                msg
            }
        }
    }

    private val web3j: Web3j get() = ArbitrumClient.web3j
    private val ensWeb3j: Web3j get() = ArbitrumClient.ensWeb3j

    // --- NEW: Clean Data Class for NFC prep ---
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
        from: String,
        to: String,
        value: BigInteger,
        data: String = "0x",
        fallback: BigInteger
    ): BigInteger = withContext(Dispatchers.IO) {
        try {
            val response = web3j.ethEstimateGas(
                org.web3j.protocol.core.methods.request.Transaction.createFunctionCallTransaction(
                    from, null, null, null, to, value, data
                )
            ).send()

            if (response.hasError()) {
                android.util.Log.w("ArbitrumClient", "Gas estimate error: ${response.error.message}, using fallback")
                return@withContext fallback
            }

            // 20% buffer
            response.amountUsed.multiply(BigInteger.valueOf(12)).divide(BigInteger.valueOf(10))
        } catch (e: Exception) {
            android.util.Log.e("ArbitrumClient", "Gas estimation failed, using fallback", e)
            fallback
        }
    }

    private suspend fun getGasProvider(gasLimit: BigInteger): StaticGasProvider = withContext(Dispatchers.IO) {
        try {
            val baseGasPrice = web3j.ethGasPrice().send().gasPrice
            // 20% Gas Price buffer (1.2x base price)
            val bufferedPrice = baseGasPrice.multiply(BigInteger.valueOf(12)).divide(BigInteger.valueOf(10))
            StaticGasProvider(bufferedPrice, gasLimit)
        } catch (e: Exception) {
            android.util.Log.e("ArbitrumClient", "Gas price fetch failed, using fallback", e)
            // Safe fallback to 0.1 Gwei if RPC node fails to return gas price
            StaticGasProvider(BigInteger.valueOf(100_000_000), gasLimit)
        }
    }

    suspend fun getBalance(address: String): BigInteger = withContext(Dispatchers.IO) {
        val response = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send()
        response.balance
    }

    suspend fun getVaultBalance(address: String): BigInteger = withContext(Dispatchers.IO) {
        val txManager = ReadonlyTransactionManager(web3j, address)
        val vault = VolticSmartWallet.load(VAULT_ADDRESS, web3j, txManager, readOnlyGasProvider)
        vault.balanceOf(address).send() ?: BigInteger.ZERO
    }

    suspend fun getVaultNonce(address: String): BigInteger = withContext(Dispatchers.IO) {
        val txManager = ReadonlyTransactionManager(web3j, address)
        val vault = VolticSmartWallet.load(VAULT_ADDRESS, web3j, txManager, readOnlyGasProvider)
        vault.nonces(address).send() ?: BigInteger.ZERO
    }

    suspend fun getReceiverAddress(rawRecipient: String): String = withContext(Dispatchers.IO) {
        val normalizedRecipient = rawRecipient.trim()
        if (normalizedRecipient.endsWith(".eth", ignoreCase = true)) {
            val address = EnsResolver(ensWeb3j).resolve(ENSNormalize.ENSIP15.normalize(normalizedRecipient))
            require(!address.isNullOrEmpty() && address != "0x0000000000000000000000000000000000000000") {
                "Invalid ENS name"
            }
            address
        } else normalizedRecipient
    }



    suspend fun getOfflinePaymentParams(
        customerAddress: String,
        toAddress: String,
        amountEth: String
    ): OfflinePaymentParams = withContext(Dispatchers.IO) {
        val vaultNonce = getVaultNonce(customerAddress)
        val eoaNonce = web3j.ethGetTransactionCount(customerAddress, DefaultBlockParameterName.PENDING).send().transactionCount
        val gasPrice = web3j.ethGasPrice().send().gasPrice.multiply(BigInteger.valueOf(12)).divide(BigInteger.valueOf(10))

        val amountWei = Convert.toWei(amountEth.ifBlank { "0" }, Convert.Unit.ETHER).toBigInteger()
        val resolvedTo = getReceiverAddress(toAddress)

        // Only estimate for Legacy transfer here. Vault is estimated during broadcast when we have the signature.
        val gasLimit = estimateGasLimit(
            from = customerAddress,
            to = resolvedTo,
            value = amountWei,
            data = "0x",
            fallback = BigInteger.valueOf(21_000)
        )

        OfflinePaymentParams(vaultNonce, eoaNonce, gasPrice, gasLimit)
    }

    suspend fun broadcastLegacyTransaction(signedTxHex: String): String = withContext(Dispatchers.IO) {
        txMutex.withLock {
            val ethResponse = web3j.ethSendRawTransaction(signedTxHex).send()
            require(!ethResponse.hasError()) { ethResponse.error.message }
            ethResponse.transactionHash
        }
    }

    suspend fun broadcastNfcVaultPayment(
        merchantCredentials: Credentials,
        customerAddress: String,
        toAddress: String,
        amountEth: String,
        nonce: BigInteger,
        deadline: BigInteger,
        signatureHex: String
    ): String = withContext(Dispatchers.IO) {
        txMutex.withLock {
            val amountWei = Convert.toWei(amountEth, Convert.Unit.ETHER).toBigInteger()
            val resolvedTo = getReceiverAddress(toAddress)

            // 1. Encode with the REAL signature so the contract won't revert during estimation
            val function = Function("executePayment", listOf(
                Address(customerAddress),
                Address(resolvedTo),
                Uint256(amountWei),
                Uint256(nonce),
                Uint256(deadline),
                DynamicBytes(Numeric.hexStringToByteArray(signatureHex))
            ), emptyList())
            val encodedData = FunctionEncoder.encode(function)

            // 2. Fetch the true gas limit, safe fallback of 150_000 for storage updates
            val trueGasLimit = estimateGasLimit(
                from = merchantCredentials.address,
                to = VAULT_ADDRESS,
                value = BigInteger.ZERO,
                data = encodedData,
                fallback = BigInteger.valueOf(150_000)
            )

            // 3. Broadcast
            val txManager = RawTransactionManager(web3j, merchantCredentials, ARBITRUM_CHAIN_ID, 40, 500L)
            val vault = VolticSmartWallet.load(VAULT_ADDRESS, web3j, txManager, getGasProvider(trueGasLimit))

            val receipt = vault.executePayment(
                customerAddress,
                resolvedTo,
                amountWei,
                nonce,
                deadline,
                Numeric.hexStringToByteArray(signatureHex)
            ).send()

            require(receipt.isStatusOK) { "Transaction reverted by Vault" }
            receipt.transactionHash
        }
    }

    suspend fun depositToVault(credentials: Credentials, amountEth: String): String = withContext(Dispatchers.IO) {
        txMutex.withLock {
            val amountWei = Convert.toWei(amountEth, Convert.Unit.ETHER).toBigInteger()
            val function = Function("deposit", listOf(Uint256(amountWei)), emptyList())
            val encodedData = FunctionEncoder.encode(function)
            val dynamicGasLimit = estimateGasLimit(credentials.address, VAULT_ADDRESS, amountWei, encodedData, BigInteger.valueOf(60_000))
            val txManager = RawTransactionManager(web3j, credentials, ARBITRUM_CHAIN_ID, 40, 500L)
            val vault = VolticSmartWallet.load(VAULT_ADDRESS, web3j, txManager, getGasProvider(dynamicGasLimit))

            val receipt = vault.deposit(amountWei).send()
            require(receipt.isStatusOK) { "Vault deposit failed" }
            receipt.transactionHash
        }
    }

    suspend fun withdrawFromVault(credentials: Credentials, amountEth: String): String = withContext(Dispatchers.IO) {
        txMutex.withLock {
            val amountWei = Convert.toWei(amountEth, Convert.Unit.ETHER).toBigInteger()
            val function = Function("withdraw", listOf(Uint256(amountWei)), emptyList())
            val encodedData = FunctionEncoder.encode(function)

            val dynamicGasLimit = estimateGasLimit(credentials.address, VAULT_ADDRESS, BigInteger.ZERO, encodedData, BigInteger.valueOf(70_000))
            val txManager = RawTransactionManager(web3j, credentials, ARBITRUM_CHAIN_ID, 40, 500L)
            val vault = VolticSmartWallet.load(VAULT_ADDRESS, web3j, txManager, getGasProvider(dynamicGasLimit))

            val receipt = vault.withdraw(amountWei).send()
            require(receipt.isStatusOK) { "Vault withdrawal failed" }
            receipt.transactionHash
        }
    }

    data class SpendLimitInfo(val amount: BigInteger, val spent: BigInteger, val period: Int)

    suspend fun getSpendLimitInfo(address: String): SpendLimitInfo = withContext(Dispatchers.IO) {
        val txManager = ReadonlyTransactionManager(web3j, address)
        val vault = VolticSmartWallet.load(VAULT_ADDRESS, web3j, txManager, readOnlyGasProvider)
        val limits = vault.spendLimits(address).send()
        val period = vault.spendPeriod(address).send()
        SpendLimitInfo(limits.component1(), limits.component2(), period.toInt())
    }

    suspend fun updateSpendLimit(credentials: Credentials, periodIndex: Int, amountEth: String): String = withContext(Dispatchers.IO) {
        txMutex.withLock {
            val amountWei = Convert.toWei(amountEth, Convert.Unit.ETHER).toBigInteger()
            val function = Function("setSpendLimit", listOf(Uint256(periodIndex.toLong()), Uint256(amountWei)), emptyList())
            val encodedData = FunctionEncoder.encode(function)

            val dynamicGasLimit = estimateGasLimit(credentials.address, VAULT_ADDRESS, BigInteger.ZERO, encodedData, BigInteger.valueOf(80_000))
            val txManager = RawTransactionManager(web3j, credentials, ARBITRUM_CHAIN_ID, 40, 500L)
            val vault = VolticSmartWallet.load(VAULT_ADDRESS, web3j, txManager, getGasProvider(dynamicGasLimit))

            val receipt = vault.setSpendLimit(BigInteger.valueOf(periodIndex.toLong()), amountWei).send()
            require(receipt.isStatusOK) { "Failed to update spending limit" }
            receipt.transactionHash
        }
    }

    suspend fun sendEth(
        credentials: Credentials,
        toAddress: String,
        amountEth: String
    ): String = withContext(Dispatchers.IO) {
        txMutex.withLock {
            val resolvedAddress = getReceiverAddress(toAddress)
            val fromAddress = credentials.address
            val amountWei = Convert.toWei(BigDecimal(amountEth), Convert.Unit.ETHER).toBigInteger()

            val gasLimit = estimateGasLimit(fromAddress, resolvedAddress, amountWei, "0x", BigInteger.valueOf(21_000))

            val nonce = web3j.ethGetTransactionCount(fromAddress, DefaultBlockParameterName.PENDING).send().transactionCount
            val baseGasPrice = web3j.ethGasPrice().send().gasPrice
            val gasPrice = baseGasPrice.multiply(BigInteger.valueOf(12)).divide(BigInteger.valueOf(10))

            val rawTransaction = RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit, resolvedAddress, amountWei)
            val signedMessage = TransactionEncoder.signMessage(rawTransaction, ARBITRUM_CHAIN_ID, credentials)

            val response = web3j.ethSendRawTransaction(Numeric.toHexString(signedMessage)).send()
            require(!response.hasError()) { "Transaction failed: ${response.error.message}" }
            response.transactionHash
        }
    }

    suspend fun executeVaultPayment(
        credentials: Credentials,
        toAddress: String,
        amountEth: String
    ): String = withContext(Dispatchers.IO) {

            val resolvedTo = getReceiverAddress(toAddress)
            val ownerAddress = credentials.address

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

    fun signVaultPayment(
        credentials: Credentials,
        to: String,
        amountEth: String,
        nonce: BigInteger,
        deadline: BigInteger
    ): String {
        val amountWei = Convert.toWei(amountEth, Convert.Unit.ETHER).toBigInteger()
        val domainTypeHash = Hash.sha3("EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)".toByteArray())
        val nameHash = Hash.sha3("VolticSmartWallet".toByteArray())
        val versionHash = Hash.sha3("1".toByteArray())
        val domainSeparator = Hash.sha3(
            Numeric.hexStringToByteArray(TypeEncoder.encode(Bytes32(domainTypeHash))) +
                    Numeric.hexStringToByteArray(TypeEncoder.encode(Bytes32(nameHash))) +
                    Numeric.hexStringToByteArray(TypeEncoder.encode(Bytes32(versionHash))) +
                    Numeric.hexStringToByteArray(TypeEncoder.encode(Uint256(ARBITRUM_CHAIN_ID))) +
                    Numeric.hexStringToByteArray(TypeEncoder.encode(Address(VAULT_ADDRESS)))
        )
        val paymentTypeHash = Hash.sha3("Payment(address owner,address to,uint256 amount,uint256 nonce,uint256 deadline)".toByteArray())
        val structHash = Hash.sha3(
            Numeric.hexStringToByteArray(TypeEncoder.encode(Bytes32(paymentTypeHash))) +
                    Numeric.hexStringToByteArray(TypeEncoder.encode(Address(credentials.address))) +
                    Numeric.hexStringToByteArray(TypeEncoder.encode(Address(to))) +
                    Numeric.hexStringToByteArray(TypeEncoder.encode(Uint256(amountWei))) +
                    Numeric.hexStringToByteArray(TypeEncoder.encode(Uint256(nonce))) +
                    Numeric.hexStringToByteArray(TypeEncoder.encode(Uint256(deadline)))
        )
        val digest = Hash.sha3(byteArrayOf(0x19, 0x01) + domainSeparator + structHash)
        val sigData = Sign.signMessage(digest, credentials.ecKeyPair, false)
        return Numeric.toHexString(sigData.r) + Numeric.toHexStringNoPrefix(sigData.s) + Numeric.toHexStringNoPrefix(byteArrayOf(sigData.v[0]))
    }

    fun signEthTransactionOffline(
        credentials: Credentials,
        toAddress: String,
        amountEth: String,
        nonce: BigInteger,
        gasPriceWei: BigInteger,
        gasLimit: BigInteger
    ): ByteArray {
        val amountWei = Convert.toWei(BigDecimal(amountEth), Convert.Unit.ETHER).toBigInteger()
        val rawTransaction = RawTransaction.createEtherTransaction(nonce, gasPriceWei, gasLimit, toAddress, amountWei)
        return TransactionEncoder.signMessage(rawTransaction, ARBITRUM_CHAIN_ID, credentials)
    }
}
