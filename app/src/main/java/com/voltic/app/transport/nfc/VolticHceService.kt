package com.voltic.app.transport.nfc

import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.voltic.app.chain.ArbitrumClient
import com.voltic.app.payload.NFCPaymentRequest
import com.voltic.app.wallet.WalletManager
import java.math.BigInteger

class VolticHceService : HostApduService() {

    companion object {
        private const val TAG = "VolticHceService"
    }

    private var awaitingSecondTap = false
    private val chain = ArbitrumClient()

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray? {
        if (commandApdu == null || commandApdu.size < 4) {
            Log.w(TAG, "Received invalid or empty APDU")
            return ApduConstants.STATUS_FAILED
        }

        Log.d(TAG, "Received APDU: ${commandApdu[1].toUByte().toString(16)}")

        return when {
            ApduConstants.isSelectAidCommand(commandApdu) -> {
                Log.i(TAG, "AID Selected - NFC Session starting")
                ApduConstants.STATUS_SUCCESS
            }
            commandApdu[1] == 0xD0.toByte() -> handleFirstTap(commandApdu)
            commandApdu[1] == 0xD1.toByte() -> handleSecondTap(commandApdu)
            else -> {
                Log.w(TAG, "Received unknown command APDU")
                ApduConstants.STATUS_FAILED
            }
        }
    }

    private fun handleFirstTap(commandApdu: ByteArray): ByteArray {
        Log.d(TAG, "Handling First Tap (Request Payment)")
        return try {
            val payloadBytes = commandApdu.drop(5).toByteArray()
            val request = NFCPaymentRequest.parse(String(payloadBytes, Charsets.UTF_8))
            Log.i(TAG, "Parsed Payment Request to: ${request.to}")

            NfcSession.startSession(request)
            launchConfirmationActivity()
            awaitingSecondTap = true

            val creds = WalletManager(this).loadExistingWallet()
                ?: throw IllegalStateException("No Wallet")

            Log.d(TAG, "Sending back public address: ${creds.address}")
            creds.address.toByteArray(Charsets.UTF_8) + ApduConstants.STATUS_SUCCESS
        } catch (e: Exception) {
            Log.e(TAG, "First Tap Failed", e)
            ApduConstants.STATUS_FAILED
        }
    }

    private fun handleSecondTap(commandApdu: ByteArray): ByteArray {
        Log.d(TAG, "Handling Second Tap (Request Signature)")
        if (!NfcSession.isAuthorized) {
            Log.w(TAG, "Second Tap received but session is NOT authorized by user")
            return ApduConstants.STATUS_NOT_READY
        }

        return try {
            val payloadBytes = commandApdu.drop(5).toByteArray()
            val parts = String(payloadBytes, Charsets.UTF_8).split("|")
            // Reader now sends BOTH nonces, since it doesn't know at tap1 which
            // path (vault vs legacy EOA) the customer will end up choosing.
            // - vaultNonce: contract's nonces[owner] — only valid for executePayment.
            // - eoaNonce: real on-chain account transaction count — only valid for
            //   a raw, directly-broadcast transaction.
            // Using the wrong one for the legacy path is what caused
            // "nonce too low" broadcast failures.
            val vaultNonce = BigInteger(parts[0])
            val eoaNonce = BigInteger(parts[1])
            val gasPrice = BigInteger(parts[2])
            Log.d(TAG, "Parsed vaultNonce: $vaultNonce, eoaNonce: $eoaNonce, gasPrice: $gasPrice")

            val creds = WalletManager(this).loadExistingWallet()!!
            val request = NfcSession.pendingRequest.value!!

            if (NfcSession.useVault) {
                Log.i(TAG, "Starting EIP-712 Vault signing for ${request.amountEth} ETH")
                val deadline = BigInteger.valueOf(System.currentTimeMillis() / 1000 + 1800)

                val signatureHex = chain.signVaultPayment(
                    creds, request.to, request.amountEth ?: "0", vaultNonce, deadline
                )

                NfcSession.clear()
                awaitingSecondTap = false

                val vaultPayload = "VAULT|$signatureHex|$vaultNonce|$deadline|${request.amountEth ?: "0"}"
                return vaultPayload.toByteArray(Charsets.UTF_8) + ApduConstants.STATUS_SUCCESS
            }

            Log.i(TAG, "Starting Offline Signing for ${request.amountEth} ETH")
            val rawSignedTx = chain.signEthTransactionOffline(
                creds, request.to, request.amountEth ?: "0", eoaNonce, gasPrice
            )

            NfcSession.clear()
            awaitingSecondTap = false

            Log.i(TAG, "Signing Complete. Sending back signed transaction.")
            rawSignedTx + ApduConstants.STATUS_SUCCESS
        } catch (e: Exception) {
            Log.e(TAG, "Second Tap Failed", e)
            ApduConstants.STATUS_FAILED
        }
    }

    private fun launchConfirmationActivity() {
        startActivity(Intent(NfcIntents.ACTION_INCOMING_PAYMENT).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            `package` = packageName
        })
    }

    override fun onDeactivated(reason: Int) {
        Log.i(TAG, "HCE Deactivated: reason=$reason")
    }
}