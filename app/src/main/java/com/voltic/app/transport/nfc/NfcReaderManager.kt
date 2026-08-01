package com.voltic.app.transport.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.util.Log
import com.voltic.app.chain.ArbitrumClient
import com.voltic.app.payload.NFCPaymentRequest
import com.voltic.contracts.VolticSmartWallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.web3j.crypto.Credentials
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.gas.StaticGasProvider
import org.web3j.utils.Convert
import org.web3j.utils.Numeric
import java.math.BigInteger

sealed class ReaderState {
    object WaitingForTap1 : ReaderState()
    data class ProcessingTap1(val address: String) : ReaderState()
    data class WaitingForTap2(val customerAddress: String, val nonce: BigInteger, val gasPrice: BigInteger) : ReaderState()
    object Broadcasting : ReaderState()
    data class Success(val txHash: String) : ReaderState()
    data class Error(val message: String) : ReaderState()
}

class NfcReaderManager(
    private val activity: Activity,
    private val request: NFCPaymentRequest,
    private val merchantCredentials: Credentials,
    private val onStateChanged: (ReaderState) -> Unit,
) : NfcAdapter.ReaderCallback {

    companion object {
        private const val TAG = "NfcReaderManager"
    }

    private var currentState: ReaderState = ReaderState.WaitingForTap1
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val web3j = ArbitrumClient.web3j

    fun start() {
        Log.i(TAG, "Starting NFC Reader for request: ${request.amountEth} ETH")
        val nfcAdapter = NfcAdapter.getDefaultAdapter(activity)
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        nfcAdapter?.enableReaderMode(activity, this, flags, null)
        updateState(ReaderState.WaitingForTap1)
    }

    fun stop() {
        Log.i(TAG, "Stopping NFC Reader")
        NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity)
        scope.cancel()
    }

    override fun onTagDiscovered(tag: android.nfc.Tag) {
        val isoDep = IsoDep.get(tag) ?: return

        try {
            when (val state = currentState) {
                is ReaderState.WaitingForTap1 -> {
                    val customerAddress = ApduTransceiver.sendPaymentRequest(isoDep, request)
                    updateState(ReaderState.ProcessingTap1(customerAddress))

                    scope.launch {
                        try {
                            val nonce = web3j.ethGetTransactionCount(customerAddress, DefaultBlockParameterName.PENDING).send().transactionCount
                            val gasPrice = web3j.ethGasPrice().send().gasPrice.multiply(BigInteger.valueOf(12)).divide(BigInteger.valueOf(10))
                            updateState(ReaderState.WaitingForTap2(customerAddress, nonce, gasPrice))
                        } catch (e: Exception) {
                            Log.e(TAG, "Network error fetching nonce/gas", e)
                            updateState(ReaderState.Error("Network error fetching nonce."))
                        }
                    }
                }
                is ReaderState.WaitingForTap2 -> {
                    isoDep.connect()
                    isoDep.timeout = 5000

                    val aidBytes = ApduConstants.AID.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    isoDep.transceive(byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aidBytes.size.toByte()) + aidBytes)

                    val payload = "${state.nonce}|${state.gasPrice}".toByteArray(Charsets.UTF_8)
                    val commandApdu = byteArrayOf(0x00, 0xD1.toByte(), 0x00, 0x00, payload.size.toByte()) + payload

                    val response = isoDep.transceive(commandApdu)
                    isoDep.close()

                    val statusWord = response.takeLast(2).toByteArray()
                    if (!statusWord.contentEquals(ApduConstants.STATUS_SUCCESS)) {
                        return
                    }

                    val rawData = response.dropLast(2).toByteArray()

                    if (rawData.isNotEmpty() && rawData[0] == 'V'.toByte()) {
                        val responseString = String(rawData, Charsets.UTF_8)
                        val parts = responseString.split("|")
                        val signatureHex = parts[1]
                        val nonce = BigInteger(parts[2])
                        val deadline = BigInteger(parts[3])
                        val amountEthFromPeer = parts[4]

                        updateState(ReaderState.Broadcasting)
                        scope.launch {
                            try {
                                ArbitrumClient.txMutex.withLock {
                                    val txManager = RawTransactionManager(web3j, merchantCredentials, ArbitrumClient.ARBITRUM_CHAIN_ID, 40, 500L)

                                    val baseGasPrice = web3j.ethGasPrice().send().gasPrice
                                    val gasPrice = baseGasPrice.multiply(BigInteger.valueOf(12)).divide(BigInteger.valueOf(10))
                                    val gasProvider = StaticGasProvider(gasPrice, BigInteger.valueOf(120_000))

                                    val vault = VolticSmartWallet.load(ArbitrumClient.VAULT_ADDRESS, web3j, txManager, gasProvider)
                                    val amountWei = Convert.toWei(amountEthFromPeer, Convert.Unit.ETHER).toBigInteger()

                                    val receipt = vault.executePayment(
                                        state.customerAddress,
                                        request.to,
                                        amountWei,
                                        nonce,
                                        deadline,
                                        Numeric.hexStringToByteArray(signatureHex)
                                    ).send()

                                    if (!receipt.isStatusOK) throw Exception("Transaction reverted")
                                    updateState(ReaderState.Success(receipt.transactionHash))
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Vault broadcast failed", e)
                                val displayMsg = ArbitrumClient.formatError(e.message)
                                updateState(ReaderState.Error("Broadcast failed: $displayMsg"))
                            }
                        }
                    } else {
                        // Legacy Raw TX
                        val signedTxHex = Numeric.toHexString(rawData)
                        updateState(ReaderState.Broadcasting)
                        scope.launch {
                            try {
                                ArbitrumClient.txMutex.withLock {
                                    val ethResponse = web3j.ethSendRawTransaction(signedTxHex).send()
                                    if (ethResponse.hasError()) throw Exception(ethResponse.error.message)
                                    updateState(ReaderState.Success(ethResponse.transactionHash))
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Legacy broadcast failed", e)
                                val displayMsg = ArbitrumClient.formatError(e.message)
                                updateState(ReaderState.Error("Broadcast failed: $displayMsg"))
                            }
                        }
                    }
                }
                else -> { }
            }
        } catch (e: Exception) {
            updateState(ReaderState.Error("Tap failed: ${e.localizedMessage}"))
        }
    }

    private fun updateState(newState: ReaderState) {
        currentState = newState
        CoroutineScope(Dispatchers.Main).launch { onStateChanged(newState) }
    }
}