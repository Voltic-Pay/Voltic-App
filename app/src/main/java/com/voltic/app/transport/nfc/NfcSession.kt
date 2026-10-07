package com.voltic.app.transport.nfc

import android.util.Log
import com.voltic.app.payload.NFCPaymentRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class HandoffKind { EOA, VAULT }

/**
 * Recorded by the HCE service the moment it hands a signed payment to the merchant's phone.
 * "Handed over" is NOT "paid": the merchant still has to broadcast, so the sender UI uses
 * this to watch the chain until the nonce we signed with is actually consumed.
 *
 * @param signedNonce nonce we signed with (EOA tx count, or the vault's nonces[owner])
 * @param expiresAtSec unix-seconds deadline after which the payment can no longer land
 *                     (vault payments only; a raw legacy tx never expires)
 */
data class Handoff(
    val kind: HandoffKind,
    val signerAddress: String,
    val signedNonce: Long,
    val expiresAtSec: Long?,
)

/**
 * Holds state BETWEEN the two NFC taps.
 */
object NfcSession {
    private const val TAG = "VolticNFC_Session"

    private val _pendingRequest = MutableStateFlow<NFCPaymentRequest?>(null)
    val pendingRequest: StateFlow<NFCPaymentRequest?> = _pendingRequest

    private val _handoff = MutableStateFlow<Handoff?>(null)
    val handoff: StateFlow<Handoff?> = _handoff

    var useVault: Boolean = false
    var isAuthorized: Boolean = false
        private set

    fun startSession(request: NFCPaymentRequest) {
        Log.i(TAG, "Starting NFC Session for request to: ${request.to}")
        _pendingRequest.value = request
        _handoff.value = null
        isAuthorized = false
        useVault = false // Default to EOA
    }

    fun updateAmount(newAmountEth: String) {
        Log.i(TAG, "Updating NFC Session amount to: $newAmountEth")
        _pendingRequest.value = _pendingRequest.value?.copy(amountEth = newAmountEth)
    }

    fun authorize() {
        Log.i(TAG, "NFC Session AUTHORIZED by user")
        isAuthorized = true
    }

    /** Call AFTER [clear] once the signed payload has been returned to the reader. */
    fun markHandedOff(handoff: Handoff) {
        Log.i(TAG, "Signed payment handed to merchant (${handoff.kind}, nonce ${handoff.signedNonce})")
        _handoff.value = handoff
    }

    /** Wipes everything, including the handoff record. Call when leaving the payment flow. */
    fun clear() {
        Log.i(TAG, "Clearing NFC Session")
        _pendingRequest.value = null
        _handoff.value = null
        isAuthorized = false
        useVault = false
    }
}
