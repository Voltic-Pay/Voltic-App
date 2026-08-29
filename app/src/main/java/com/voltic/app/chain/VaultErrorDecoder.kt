// this file is AI generated ... no
package com.voltic.app.chain

import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric

/**
 * Decodes VolticSmartWallet's custom Solidity errors from raw revert data.
 *
 * Solidity custom errors revert with: 4-byte selector = first 4 bytes of
 * keccak256("ErrorName(paramTypes)"), optionally followed by abi-encoded params.
 */
object VaultErrorDecoder {

    private fun selector(signature: String): String =
        Numeric.toHexString(Hash.sha3(signature.toByteArray())).substring(0, 10)

    private val ERR_SELECTOR = "0x08c379a0" // Error(string)
    private val PANIC_SELECTOR = "0x4e487b71" // Panic(uint256)

    private val KNOWN_ERRORS: Map<String, String> = mapOf(
        selector("ZeroAmount()") to "Amount can't be zero.",
        selector("ZeroAddress()") to "Recipient address can't be empty.",
        selector("InvalidToAddress()") to "That recipient address isn't valid.",
        selector("InsufficientBalance()") to "Not enough balance in the vault for this.",
        selector("WalletDisabled()") to "This wallet has been disabled by its owner.",
        selector("ExpiredDeadline()") to "This payment request expired — try again.",
        selector("DeadlineTooFarInFuture()") to "Payment deadline is too far in the future.",
        selector("NonceAlreadyUsed()") to "This payment was already processed.",
        selector("InvalidSignature()") to "Payment signature didn't verify — try again.",
        selector("SpendLimitExceeded()") to "This would go over your spending limit for this period.",
        selector("ReentrancyGuardReentrantCall()") to "Another vault operation is already in progress."
    )

    /**
     * Pulls a human-readable message out of raw revert-data hex.
     */
    fun decode(revertDataHex: String?): String? {
        if (revertDataHex.isNullOrBlank()) return null

        // Find the first 0x-prefixed hex string of reasonable length
        val hexMatch = Regex("0x[0-9a-fA-F]{8,}").find(revertDataHex) ?: return null
        val fullHex = hexMatch.value
        val selector = fullHex.substring(0, 10).lowercase()

        // 1. Check Custom Errors
        KNOWN_ERRORS[selector]?.let { return it }

        // 2. Check standard Error(string)
        if (selector == ERR_SELECTOR && fullHex.length > 10) {
            try {
                val encodedData = fullHex.substring(10)
                @Suppress("UNCHECKED_CAST")
                val decoded = FunctionReturnDecoder.decode(
                    encodedData,
                    listOf(object : TypeReference<Utf8String>() {}) as List<TypeReference<org.web3j.abi.datatypes.Type<*>>>
                )
                if (decoded.isNotEmpty()) {
                    return (decoded[0] as Utf8String).value
                }
            } catch (_: Exception) {
                // fall through
            }
        }

        // 3. Check standard Panic(uint256)
        if (selector == PANIC_SELECTOR && fullHex.length > 10) {
            try {
                val encodedData = fullHex.substring(10)
                @Suppress("UNCHECKED_CAST")
                val decoded = FunctionReturnDecoder.decode(
                    encodedData,
                    listOf(object : TypeReference<Uint256>() {}) as List<TypeReference<org.web3j.abi.datatypes.Type<*>>>
                )
                if (decoded.isNotEmpty()) {
                    val code = (decoded[0] as Uint256).value
                    return "System Panic (0x${code.toString(16)})"
                }
            } catch (_: Exception) {
                // fall through
            }
        }

        return null
    }
}
