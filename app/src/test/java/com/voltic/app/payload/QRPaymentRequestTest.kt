package com.voltic.app.payload

import com.voltic.app.chain.ChainConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QRPaymentRequestTest {

    private val testAddress = "0x1234567890123456789012345678901234567890"

    @Test
    fun `toUri wraps ERC-681 in the Voltic domain with amount and chainId`() {
        val request = QRPaymentRequest(
            to = testAddress,
            amountEth = "0.01",
            chainId = 421614L
        )

        val uri = request.toUri()
        assertEquals("https://voltic-pay.github.io/ethereum:$testAddress@421614?value=0.01e18", uri)
    }

    @Test
    fun `toUri wraps ERC-681 in the Voltic domain without amount`() {
        val request = QRPaymentRequest(
            to = testAddress,
            amountEth = null,
            chainId = 421614L
        )

        val uri = request.toUri()
        assertEquals("https://voltic-pay.github.io/ethereum:$testAddress@421614", uri)
    }

    @Test
    fun `parse reads its own toUri output`() {
        val original = QRPaymentRequest(to = testAddress, amountEth = "0.01", chainId = 421614L)
        val parsed = QRPaymentRequest.parse(original.toUri())

        assertEquals(original, parsed)
    }

    @Test
    fun `parse parses Voltic https wrapper`() {
        val raw = "https://voltic-pay.github.io/ethereum:$testAddress@421614?value=1e16"
        val parsed = QRPaymentRequest.parse(raw)

        assertEquals(testAddress, parsed.to)
        assertEquals("0.01", parsed.amountEth)
        assertEquals(421614L, parsed.chainId)
    }

    @Test
    fun `parse parses standard ERC-681 native ETH request with scientific notation`() {
        val raw = "ethereum:0xfb6916095ca1df60bb79Ce92ce3ea74c37c5d359?value=2.014e18"
        val parsed = QRPaymentRequest.parse(raw)

        assertEquals("0xfb6916095ca1df60bb79Ce92ce3ea74c37c5d359", parsed.to)
        assertEquals("2.014", parsed.amountEth)
        assertEquals(ChainConfig.current.chainId, parsed.chainId)
    }

    @Test
    fun `parse parses ERC-681 URI with chainId and scientific notation`() {
        val raw = "ethereum:$testAddress@421614?value=1e16"
        val parsed = QRPaymentRequest.parse(raw)

        assertEquals(testAddress, parsed.to)
        assertEquals("0.01", parsed.amountEth)
        assertEquals(421614L, parsed.chainId)
    }

    @Test
    fun `parse parses ERC-681 URI with pay- prefix`() {
        val raw = "ethereum:pay-$testAddress@1?value=1e18"
        val parsed = QRPaymentRequest.parse(raw)

        assertEquals(testAddress, parsed.to)
        assertEquals("1", parsed.amountEth)
        assertEquals(1L, parsed.chainId)
    }

    @Test
    fun `parse parses ERC-681 URI with ENS name`() {
        val raw = "ethereum:alice.eth@1"
        val parsed = QRPaymentRequest.parse(raw)

        assertEquals("alice.eth", parsed.to)
        assertNull(parsed.amountEth)
        assertEquals(1L, parsed.chainId)
    }

    @Test
    fun `parse parses ERC-681 ERC-20 transfer call`() {
        val contractAddress = "0x89205a3a3b2a69de6dbf7f01ed13b2108b2c43e7"
        val recipientAddress = "0x8e23ee67d1332ad560396262c48ffbb01f93d052"
        val raw = "ethereum:$contractAddress@1/transfer?address=$recipientAddress&uint256=1e18"

        val parsed = QRPaymentRequest.parse(raw)

        assertEquals(recipientAddress, parsed.to)
        assertEquals("1", parsed.amountEth)
        assertEquals(1L, parsed.chainId)
    }

    @Test
    fun `parse parses bare Ethereum address`() {
        val parsed = QRPaymentRequest.parse(testAddress)

        assertEquals(testAddress, parsed.to)
        assertNull(parsed.amountEth)
        assertEquals(ChainConfig.current.chainId, parsed.chainId)
    }

    @Test
    fun `parse parses bare ENS name`() {
        val parsed = QRPaymentRequest.parse("alice.eth")

        assertEquals("alice.eth", parsed.to)
        assertNull(parsed.amountEth)
        assertEquals(ChainConfig.current.chainId, parsed.chainId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse throws for invalid string`() {
        QRPaymentRequest.parse("invalid_content_here")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse throws for old Voltic link format`() {
        QRPaymentRequest.parse("https://voltic-pay.github.io/pay?to=$testAddress&amount=0.01&chainId=421614")
    }
}