package com.voltic.app.transport.nfc


internal object ApduConstants {

    // Register this same AID in res/xml/apdu_service.xml <aid-group><aid-filter>
    const val AID = "F0564F4C544943"  // random to be honest
    // Standard SELECT AID command header (first 5 bytes are fixed by the spec)
    private val SELECT_APDU_HEADER = byteArrayOf(
        0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte()
    )

    val STATUS_SUCCESS: ByteArray = byteArrayOf(0x90.toByte(), 0x00.toByte())
    val STATUS_FAILED: ByteArray = byteArrayOf(0x6F.toByte(), 0x00.toByte())
    // custom status word we made up to mean "confirmed but not signed yet, tap again"
    val STATUS_NOT_READY: ByteArray = byteArrayOf(0x91.toByte(), 0x00.toByte())

    fun isSelectAidCommand(apdu: ByteArray): Boolean {
        if (apdu.size < SELECT_APDU_HEADER.size) return false
        return SELECT_APDU_HEADER.indices.all { apdu[it] == SELECT_APDU_HEADER[it] }
    }
}