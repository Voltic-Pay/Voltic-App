package com.voltic.app.transport.nfc

import com.voltic.app.payload.NFCPaymentRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcApduTest {

    private val testAddress = "0x1234567890123456789012345678901234567890"

    private val aidBytes = ApduConstants.AID.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    // Same layout ApduTransceiver builds: CLA INS P1 P2 Lc + data
    private fun command(ins: Int, data: ByteArray): ByteArray =
        byteArrayOf(0x00, ins.toByte(), 0x00, 0x00, data.size.toByte()) + data

    private val selectApdu =
        byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aidBytes.size.toByte()) + aidBytes

    // ---- SELECT AID detection ----

    @Test
    fun `isSelectAidCommand accepts the SELECT built from our AID`() {
        assertTrue(ApduConstants.isSelectAidCommand(selectApdu))
    }

    @Test
    fun `isSelectAidCommand rejects empty and too-short input`() {
        assertFalse(ApduConstants.isSelectAidCommand(byteArrayOf()))
        assertFalse(ApduConstants.isSelectAidCommand(byteArrayOf(0x00, 0xA4.toByte(), 0x04)))
    }

    @Test
    fun `isSelectAidCommand rejects payment commands`() {
        assertFalse(ApduConstants.isSelectAidCommand(command(0xD0, "x".toByteArray())))
        assertFalse(ApduConstants.isSelectAidCommand(command(0xD1, "x".toByteArray())))
    }

    @Test
    fun `isSelectAidCommand rejects a wrong header byte`() {
        val wrongClass = selectApdu.copyOf().also { it[0] = 0x80.toByte() }
        val wrongP1 = selectApdu.copyOf().also { it[2] = 0x00 }
        assertFalse(ApduConstants.isSelectAidCommand(wrongClass))
        assertFalse(ApduConstants.isSelectAidCommand(wrongP1))
    }

    // ---- AID sanity ----

    @Test
    fun `AID is valid hex and within the ISO 7816 5 to 16 byte range`() {
        assertEquals(0, ApduConstants.AID.length % 2)
        assertTrue(ApduConstants.AID.all { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' })
        assertTrue(aidBytes.size in 5..16)
    }

    // ---- Status words ----

    @Test
    fun `status words match the wire protocol and are all distinct`() {
        assertArrayEquals(byteArrayOf(0x90.toByte(), 0x00), ApduConstants.STATUS_SUCCESS)
        assertArrayEquals(byteArrayOf(0x6F, 0x00), ApduConstants.STATUS_FAILED)
        assertArrayEquals(byteArrayOf(0x91.toByte(), 0x00), ApduConstants.STATUS_NOT_READY)

        val all = listOf(ApduConstants.STATUS_SUCCESS, ApduConstants.STATUS_FAILED, ApduConstants.STATUS_NOT_READY)
            .map { it.toList() }
        assertEquals(3, all.toSet().size)
    }

    // ---- Tap 1 request framing (what the reader sends, what the HCE decodes) ----

    @Test
    fun `tap 1 APDU decodes back to the original payment request`() {
        val original = NFCPaymentRequest(to = testAddress, amountEth = "0.01", chainId = 421614L)
        val apdu = command(0xD0, original.encode().toByteArray(Charsets.UTF_8))

        // HCE side: instruction byte routes to tap 1, payload starts after the 5 byte header
        assertEquals(0xD0.toByte(), apdu[1])
        assertFalse(ApduConstants.isSelectAidCommand(apdu))
        val decoded = NFCPaymentRequest.parse(String(apdu.drop(5).toByteArray(), Charsets.UTF_8))

        assertEquals(original, decoded)
    }

    @Test
    fun `tap 1 APDU without an amount decodes with null amount`() {
        val original = NFCPaymentRequest(to = testAddress, amountEth = null, chainId = 42161L)
        val apdu = command(0xD0, original.encode().toByteArray(Charsets.UTF_8))

        val decoded = NFCPaymentRequest.parse(String(apdu.drop(5).toByteArray(), Charsets.UTF_8))

        assertEquals(original, decoded)
    }

    @Test
    fun `tap 1 length byte matches the payload size`() {
        val request = NFCPaymentRequest(to = testAddress, amountEth = "0.01", chainId = 421614L)
        val payload = request.encode().toByteArray(Charsets.UTF_8)
        val apdu = command(0xD0, payload)

        assertEquals(payload.size, apdu[4].toInt() and 0xFF)
        assertEquals(5 + payload.size, apdu.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `tap 1 APDU with a garbage payload is rejected by parse`() {
        val apdu = command(0xD0, "not a payment request".toByteArray())
        NFCPaymentRequest.parse(String(apdu.drop(5).toByteArray(), Charsets.UTF_8))
    }

    // ---- Tap 2 request framing ----

    @Test
    fun `tap 2 payload splits into vaultNonce, eoaNonce, gasPrice, gasLimit`() {
        val payload = "7|12|100000000|21000".toByteArray(Charsets.UTF_8)
        val apdu = command(0xD1, payload)

        assertEquals(0xD1.toByte(), apdu[1])
        val parts = String(apdu.drop(5).toByteArray(), Charsets.UTF_8).split("|")

        assertEquals(listOf("7", "12", "100000000", "21000"), parts)
    }
}