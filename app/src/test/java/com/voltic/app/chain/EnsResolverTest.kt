package com.voltic.app.chain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EnsResolverTest {

    @Test
    fun testGetReceiverAddress_withRawAddress_returnsUnchanged() = runBlocking {
        val address = "0xd8da6bf26964af9d7eed9e03e53415d37aa96045"
        val result = EnsResolver.getReceiverAddress(address)
        assertEquals(address, result)
    }

    @Test
    fun testGetReceiverAddress_withNonEthName_returnsUnchanged() = runBlocking {
        val input = "some-random-string"
        val result = EnsResolver.getReceiverAddress(input)
        assertEquals(input, result)
    }

    @Test
    fun testResolve_withInvalidEthName_throwsException() = runBlocking {
        try {
            EnsResolver.resolve("invalid-name")
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("Not an ENS name", e.message)
        }
    }

    @Test
    fun testResolve_live() = runBlocking {
        // This test requires internet access and valid RPCs in ChainConfig
        val address = EnsResolver.resolve("vitalik.eth")
        assertEquals("0xd8da6bf26964af9d7eed9e03e53415d37aa96045", address.lowercase())
    }

    @Test
    fun testResolve_withCaseAndWhitespace() = runBlocking {
        // " VITALIK.eth " should normalize to "vitalik.eth"
        val address = EnsResolver.resolve(" VITALIK.eth ")
        assertEquals("0xd8da6bf26964af9d7eed9e03e53415d37aa96045", address.lowercase())
    }

    @Test
    fun testGetVerifiedReverseRecord_invalidOrOffline() = runBlocking {
        val name = EnsResolver.getVerifiedReverseRecord("0x0000000000000000000000000000000000000000")
        assertNull(name)
    }
}
