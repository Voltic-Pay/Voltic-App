package com.voltic.app.transport.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.util.Log
import com.voltic.app.chain.ArbitrumClient
import com.voltic.app.payload.NFCPaymentRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.web3j.crypto.Credentials
import java.math.BigInteger

sealed class ReaderState {
    object WaitingForTap1 : ReaderState()
    data class ProcessingTap1(val address: String) : ReaderState()

    // NOTE: two separate nonces are carried here on purpose.
    // - vaultNonce: the contract's per-owner Payment counter (nonces[owner] in
    //   VolticSmartWallet.sol). Only valid for the meta-tx / executePayment path.
    // - eoaNonce: the account's real on-chain transaction count. Only valid for
    //   the legacy/direct raw-transaction path (signEthTransactionOffline).
    // We don't know at tap1 which path the customer will choose (that's decided
    // on their phone, after tap1, via the spend-limit toggle), so we fetch both
    // up front and let tap2 / the HCE service pick the correct one.
    data class WaitingForTap2(
        val customerAddress: String,
        val params: ArbitrumClient.OfflinePaymentParams
    ) : ReaderState()

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
    private val chain = ArbitrumClient()

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
                            // CLEAN: Ask the chain helper for everything we need
                            val params = chain.getOfflinePaymentParams(
                                customerAddress = customerAddress,
                                toAddress = request.to,
                                amountEth = request.amountEth ?: "0"
                            )
                            updateState(ReaderState.WaitingForTap2(customerAddress, params))
                        } catch (e: Exception) {
                            Log.e(TAG, "Network error fetching prep parameters", e)
                            updateState(ReaderState.Error("Network error fetching required data."))
                        }
                    }
                }
                is ReaderState.WaitingForTap2 -> {
                    isoDep.connect()
                    isoDep.timeout = 5000

                    val aidBytes = ApduConstants.AID.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    isoDep.transceive(byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aidBytes.size.toByte()) + aidBytes)

                    val payload = "${state.params.vaultNonce}|${state.params.eoaNonce}|${state.params.gasPrice}|${state.params.gasLimit}".toByteArray(Charsets.UTF_8)
                    val commandApdu = byteArrayOf(0x00, 0xD1.toByte(), 0x00, 0x00, payload.size.toByte()) + payload

                    val response = isoDep.transceive(commandApdu)
                    isoDep.close()

                    val statusWord = response.takeLast(2).toByteArray()
                    if (!statusWord.contentEquals(ApduConstants.STATUS_SUCCESS)) {
                        updateState(ReaderState.Error("Customer device not ready. Tap again."))
                        return
                    }

                    val rawData = response.dropLast(2).toByteArray()

                    if (rawData.isNotEmpty() && rawData[0] == 'V'.code.toByte()) {
                        // --- VAULT PATH ---
                        val responseString = String(rawData, Charsets.UTF_8)
                        val parts = responseString.split("|")
                        val signatureHex = parts[1]
                        val nonce = BigInteger(parts[2])
                        val deadline = BigInteger(parts[3])
                        val amountEthFromPeer = parts[4]

                        updateState(ReaderState.Broadcasting)
                        scope.launch {
                            try {
                                // CLEAN: Pass off all raw variables to the chain layer!
                                val txHash = chain.broadcastNfcVaultPayment(
                                    merchantCredentials = merchantCredentials,
                                    customerAddress = state.customerAddress,
                                    toAddress = request.to,
                                    amountEth = amountEthFromPeer,
                                    nonce = nonce,
                                    deadline = deadline,
                                    signatureHex = signatureHex
                                )
                                updateState(ReaderState.Success(txHash))
                            } catch (e: Exception) {
                                Log.e(TAG, "Vault broadcast failed", e)
                                updateState(ReaderState.Error("Broadcast failed: ${ArbitrumClient.formatError(e.message)}"))
                            }
                        }
                    } else {
                        // --- LEGACY PATH ---
                        val signedTxHex = org.web3j.utils.Numeric.toHexString(rawData) // only utility import needed
                        updateState(ReaderState.Broadcasting)
                        scope.launch {
                            try {
                                // CLEAN: Pass the hex to the chain layer!
                                val txHash = chain.broadcastLegacyTransaction(signedTxHex)
                                updateState(ReaderState.Success(txHash))
                            } catch (e: Exception) {
                                Log.e(TAG, "Legacy broadcast failed", e)
                                updateState(ReaderState.Error("Broadcast failed: ${ArbitrumClient.formatError(e.message)}"))
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
