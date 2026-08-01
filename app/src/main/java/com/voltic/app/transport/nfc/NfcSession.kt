package com.voltic.app.transport.nfc

import android.util.Log
import com.voltic.app.payload.NFCPaymentRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds state BETWEEN the two NFC taps.
 */
object NfcSession {
    private const val TAG = "VolticNFC_Session"

    private val _pendingRequest = MutableStateFlow<NFCPaymentRequest?>(null)
    val pendingRequest: StateFlow<NFCPaymentRequest?> = _pendingRequest

    var useVault: Boolean = false
    var isAuthorized: Boolean = false
        private set

    fun startSession(request: NFCPaymentRequest) {
        Log.i(TAG, "Starting NFC Session for request to: ${request.to}")
        _pendingRequest.value = request
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

    fun clear() {
        Log.i(TAG, "Clearing NFC Session")
        _pendingRequest.value = null
        isAuthorized = false
        useVault = false
    }
}
