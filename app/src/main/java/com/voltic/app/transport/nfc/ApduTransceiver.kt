package com.voltic.app.transport.nfc

import android.nfc.tech.IsoDep
import android.util.Log
import com.voltic.app.payload.NFCPaymentRequest
import org.web3j.utils.Numeric
import java.math.BigInteger

object ApduTransceiver {

    private const val TAG = "ApduTransceiver"

    private fun buildSelectApdu(): ByteArray {
        val aidBytes = ApduConstants.AID.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aidBytes.size.toByte()) + aidBytes
    }

    /** Tap 1: Sends request, returns Customer's Public Address. */
    fun sendPaymentRequest(isoDep: IsoDep, request: NFCPaymentRequest): String {
        Log.d(TAG, "Connecting to Peer phone for Tap 1...")
        isoDep.connect()
        isoDep.timeout = 5000
        
        Log.d(TAG, "Sending SELECT AID command")
        isoDep.transceive(buildSelectApdu())

        val payloadBytes = request.encode().toByteArray(Charsets.UTF_8)
        val commandApdu = byteArrayOf(0x00, 0xD0.toByte(), 0x00, 0x00, payloadBytes.size.toByte()) + payloadBytes

        Log.d(TAG, "Sending Payment Request: ${request.amountEth} ETH")
        val response = isoDep.transceive(commandApdu)
        isoDep.close()

        val statusWord = response.takeLast(2).toByteArray()
        if (!statusWord.contentEquals(ApduConstants.STATUS_SUCCESS)) {
            Log.e(TAG, "Tap 1 failed. Status: ${statusWord.joinToString("") { "%02x".format(it) }}")
            throw IllegalStateException("Tap 1 rejected by customer phone")
        }
        
        val result = String(response.dropLast(2).toByteArray(), Charsets.UTF_8)
        Log.i(TAG, "Tap 1 SUCCESS. Peer Address: $result")
        return result
    }

    /** Tap 2: Sends Nonce+Gas, returns Signed Transaction Hex. */
    fun requestSignedResult(isoDep: IsoDep, nonce: BigInteger, gasPrice: BigInteger): String? {
        Log.d(TAG, "Connecting to Peer phone for Tap 2...")
        isoDep.connect()
        isoDep.timeout = 5000
        
        Log.d(TAG, "Sending SELECT AID command")
        isoDep.transceive(buildSelectApdu())

        val payload = "${nonce}|${gasPrice}".toByteArray(Charsets.UTF_8)
        val commandApdu = byteArrayOf(0x00, 0xD1.toByte(), 0x00, 0x00, payload.size.toByte()) + payload

        Log.d(TAG, "Sending Request for Signed TX. Nonce=$nonce")
        val response = isoDep.transceive(commandApdu)
        isoDep.close()

        val statusWord = response.takeLast(2).toByteArray()
        if (!statusWord.contentEquals(ApduConstants.STATUS_SUCCESS)) {
            Log.w(TAG, "Tap 2 NOT SUCCESSFUL. Status: ${statusWord.joinToString("") { "%02x".format(it) }}")
            return null
        }

        val rawTxBytes = response.dropLast(2).toByteArray()
        Log.i(TAG, "Tap 2 SUCCESS. Received ${rawTxBytes.size} bytes of signed transaction")
        return Numeric.toHexString(rawTxBytes) // Convert back to hex for Web3j broadcast
    }
}
