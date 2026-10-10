package com.voltic.app.payload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NFCPaymentRequestTest {

    private val testAddress = "0x1234567890123456789012345678901234567890"

    @Test
    fun `encode joins fields with pipes`() {
        val request = NFCPaymentRequest(to = testAddress, amountEth = "0.01", chainId = 421614L)

        assertEquals("$testAddress|0.01|421614", request.encode())
    }

    @Test
    fun `encode leaves amount empty when null`() {
        val request = NFCPaymentRequest(to = testAddress, amountEth = null, chainId = 421614L)

        assertEquals("$testAddress||421614", request.encode())
    }

    @Test
    fun `parse reads its own encode output`() {
        val original = NFCPaymentRequest(to = testAddress, amountEth = "0.01", chainId = 421614L)

        assertEquals(original, NFCPaymentRequest.parse(original.encode()))
    }

    @Test
    fun `parse turns empty amount into null`() {
        val parsed = NFCPaymentRequest.parse("$testAddress||42161")

        assertNull(parsed.amountEth)
        assertEquals(42161L, parsed.chainId)
    }

    @Test
    fun `parse trims surrounding whitespace`() {
        val parsed = NFCPaymentRequest.parse("  $testAddress|1|42161\n")

        assertEquals(testAddress, parsed.to)
        assertEquals("1", parsed.amountEth)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse throws on wrong number of fields`() {
        NFCPaymentRequest.parse("$testAddress|0.01")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse throws on invalid address`() {
        NFCPaymentRequest.parse("0x1234|0.01|42161")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse throws on ENS name since NFC needs a raw address`() {
        NFCPaymentRequest.parse("alice.eth|0.01|42161")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse throws on non-numeric chain id`() {
        NFCPaymentRequest.parse("$testAddress|0.01|abc")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse throws on zero chain id`() {
        NFCPaymentRequest.parse("$testAddress|0.01|0")
    }
}