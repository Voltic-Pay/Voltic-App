package com.voltic.app.chain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.voltic.contracts.VolticSmartWallet
import io.github.adraffy.ens.ENSNormalize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.abi.TypeEncoder
import org.web3j.abi.datatypes.Address
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

        // Read-only gas provider: zero gas price, 300k limit. Safe, free, and incredibly fast.
        private val readOnlyGasProvider = StaticGasProvider(BigInteger.ZERO, BigInteger.valueOf(300_000))

        /**
         * Maps low-level RPC error strings to user-friendly messages.
         */
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
        try {
            val txManager = ReadonlyTransactionManager(web3j, address)
            val vault = VolticSmartWallet.load(VAULT_ADDRESS, web3j, txManager, readOnlyGasProvider)
            vault.balanceOf(address).send() ?: BigInteger.ZERO
        } catch (e: Exception) {
            android.util.Log.e("ArbitrumClient", "Vault balance fetch failed", e)
            BigInteger.ZERO
        }
    }

    suspend fun getVaultNonce(address: String): BigInteger = withContext(Dispatchers.IO) {
        try {
            val txManager = ReadonlyTransactionManager(web3j, address)
            val vault = VolticSmartWallet.load(VAULT_ADDRESS, web3j, txManager, readOnlyGasProvider)
            vault.nonces(address).send() ?: BigInteger.ZERO
        } catch (e: Exception) {
            android.util.Log.e("ArbitrumClient", "Vault nonce fetch failed", e)
            BigInteger.ZERO
        }
    }

    suspend fun getReceiverAddress(rawRecipient: String): String = withContext(Dispatchers.IO) {
        val normalizedRecipient = rawRecipient.trim()
        if (normalizedRecipient.endsWith(".eth", ignoreCase = true)) {
            val ensResolver = EnsResolver(ensWeb3j)
            val ensName = ENSNormalize.ENSIP15.normalize(normalizedRecipient)
            val address = ensResolver.resolve(ensName)
            if (address == null || address == "0x0000000000000000000000000000000000000000") {
                throw IllegalArgumentException("Invalid or unregistered ENS name: $rawRecipient")
            }
            address
        } else {
            normalizedRecipient
        }
    }

    suspend fun depositToVault(credentials: Credentials, amountEth: String): String = withContext(Dispatchers.IO) {
        txMutex.withLock {
            val txManager = RawTransactionManager(web3j, credentials, ARBITRUM_CHAIN_ID, 40, 500L)
            val gasProvider = getGasProvider(BigInteger.valueOf(60_000))
            val vault = VolticSmartWallet.load(VAULT_ADDRESS, web3j, txManager, gasProvider)
            val amountWei = Convert.toWei(amountEth, Convert.Unit.ETHER).toBigInteger()
            val receipt = vault.deposit(amountWei).send()
            if (!receipt.isStatusOK) {
                android.util.Log.e("ArbitrumClient", "Vault deposit status failed")
                throw IllegalStateException("Vault deposit failed")
            }
            receipt.transactionHash
        }
    }

    suspend fun withdrawFromVault(credentials: Credentials, amountEth: String): String = withContext(Dispatchers.IO) {
        txMutex.withLock {
            val txManager = RawTransactionManager(web3j, credentials, ARBITRUM_CHAIN_ID, 40, 500L)
            val gasProvider = getGasProvider(BigInteger.valueOf(70_000))
            val vault = VolticSmartWallet.load(VAULT_ADDRESS, web3j, txManager, gasProvider)
            val amountWei = Convert.toWei(amountEth, Convert.Unit.ETHER).toBigInteger()
            val receipt = vault.withdraw(amountWei).send()
            if (!receipt.isStatusOK) {
                android.util.Log.e("ArbitrumClient", "Vault withdrawal status failed")
                throw IllegalStateException("Vault withdrawal failed")
            }
            receipt.transactionHash
        }
    }

    data class SpendLimitInfo(val amount: BigInteger, val spent: BigInteger, val period: Int)

    suspend fun getSpendLimitInfo(address: String): SpendLimitInfo = withContext(Dispatchers.IO) {
        try {
            val txManager = ReadonlyTransactionManager(web3j, address)
            val vault = VolticSmartWallet.load(VAULT_ADDRESS, web3j, txManager, readOnlyGasProvider)
            val limits = vault.spendLimits(address).send()
            val period = vault.spendPeriod(address).send()
            SpendLimitInfo(limits.component1(), limits.component2(), period.toInt())
        } catch (e: Exception) {
            android.util.Log.e("ArbitrumClient", "Spend limit info fetch failed", e)
            SpendLimitInfo(BigInteger.ZERO, BigInteger.ZERO, 0)
        }
    }

    suspend fun updateSpendLimit(credentials: Credentials, periodIndex: Int, amountEth: String): String = withContext(Dispatchers.IO) {
        txMutex.withLock {
            val txManager = RawTransactionManager(web3j, credentials, ARBITRUM_CHAIN_ID, 40, 500L)
            val gasProvider = getGasProvider(BigInteger.valueOf(80_000))
            val vault = VolticSmartWallet.load(VAULT_ADDRESS, web3j, txManager, gasProvider)

            val amountWei = Convert.toWei(amountEth, Convert.Unit.ETHER).toBigInteger()
            val receipt = vault.setSpendLimit(BigInteger.valueOf(periodIndex.toLong()), amountWei).send()

            if (!receipt.isStatusOK) {
                android.util.Log.e("ArbitrumClient", "Set spend limit status failed")
                throw IllegalStateException("Failed to update spending limit")
            }
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

            val nonce = web3j.ethGetTransactionCount(fromAddress, DefaultBlockParameterName.PENDING).send().transactionCount
            val amountWei = Convert.toWei(BigDecimal(amountEth), Convert.Unit.ETHER).toBigInteger()

            val baseGasPrice = web3j.ethGasPrice().send().gasPrice
            val gasPrice = baseGasPrice.multiply(BigInteger.valueOf(12)).divide(BigInteger.valueOf(10))
            val gasLimit = BigInteger.valueOf(21_000)

            val rawTransaction = RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit, resolvedAddress, amountWei)
            val signedMessage = TransactionEncoder.signMessage(rawTransaction, ARBITRUM_CHAIN_ID, credentials)
            val hexValue = Numeric.toHexString(signedMessage)

            val response = web3j.ethSendRawTransaction(hexValue).send()
            if (response.hasError()) {
                android.util.Log.e("ArbitrumClient", "sendEth failed: ${response.error.message}")
                throw IllegalStateException("Transaction failed: ${response.error.message}")
            }
            response.transactionHash
        }
    }

    suspend fun executeVaultPayment(
        credentials: Credentials,
        toAddress: String,
        amountEth: String
    ): String = withContext(Dispatchers.IO) {
        txMutex.withLock {
            val resolvedTo = getReceiverAddress(toAddress)
            val ownerAddress = credentials.address

            val nonce = getVaultNonce(ownerAddress)
            val deadline = BigInteger.valueOf(System.currentTimeMillis() / 1000 + 1800)
            val signatureHex = signVaultPayment(credentials, resolvedTo, amountEth, nonce, deadline)
            val amountWei = Convert.toWei(amountEth, Convert.Unit.ETHER).toBigInteger()

            val txManager = RawTransactionManager(web3j, credentials, ARBITRUM_CHAIN_ID, 40, 500L)
            val gasProvider = getGasProvider(BigInteger.valueOf(120_000))
            val vault = VolticSmartWallet.load(VAULT_ADDRESS, web3j, txManager, gasProvider)

            val receipt = vault.executePayment(
                ownerAddress, resolvedTo, amountWei, nonce, deadline,
                Numeric.hexStringToByteArray(signatureHex)
            ).send()

            if (!receipt.isStatusOK) {
                android.util.Log.e("ArbitrumClient", "Vault payment execute failed")
                throw IllegalStateException("Vault payment failed")
            }
            receipt.transactionHash
        }
    }

    fun signVaultPayment(
        credentials: Credentials,
        to: String,
        amountEth: String,
        nonce: BigInteger,
        deadline: BigInteger
    ): String {
        val amountWei = Convert.toWei(amountEth, Convert.Unit.ETHER).toBigInteger()

        // 1. Domain Separator
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

        // 2. Struct Hash
        val paymentTypeHash = Hash.sha3("Payment(address owner,address to,uint256 amount,uint256 nonce,uint256 deadline)".toByteArray())
        val structHash = Hash.sha3(
            Numeric.hexStringToByteArray(TypeEncoder.encode(Bytes32(paymentTypeHash))) +
                    Numeric.hexStringToByteArray(TypeEncoder.encode(Address(credentials.address))) +
                    Numeric.hexStringToByteArray(TypeEncoder.encode(Address(to))) +
                    Numeric.hexStringToByteArray(TypeEncoder.encode(Uint256(amountWei))) +
                    Numeric.hexStringToByteArray(TypeEncoder.encode(Uint256(nonce))) +
                    Numeric.hexStringToByteArray(TypeEncoder.encode(Uint256(deadline)))
        )

        // 3. Final EIP-712 Digest
        val digest = Hash.sha3(
            byteArrayOf(0x19, 0x01) + domainSeparator + structHash
        )

        // 4. Sign
        val sigData = Sign.signMessage(digest, credentials.ecKeyPair, false)

        val r = sigData.r
        val s = sigData.s
        val v = sigData.v

        return Numeric.toHexString(r) + Numeric.toHexStringNoPrefix(s) + Numeric.toHexStringNoPrefix(byteArrayOf(v[0]))
    }

    fun signEthTransactionOffline(
        credentials: Credentials,
        toAddress: String,
        amountEth: String,
        nonce: BigInteger,
        gasPriceWei: BigInteger
    ): ByteArray {
        val amountWei = Convert.toWei(BigDecimal(amountEth), Convert.Unit.ETHER).toBigInteger()
        val gasLimit = BigInteger.valueOf(21_000)
        val rawTransaction = RawTransaction.createEtherTransaction(nonce, gasPriceWei, gasLimit, toAddress, amountWei)
        return TransactionEncoder.signMessage(rawTransaction, ARBITRUM_CHAIN_ID, credentials)
    }
}