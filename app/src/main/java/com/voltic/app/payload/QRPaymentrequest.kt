package com.voltic.app.payload

import com.voltic.app.chain.ChainConfig
import io.github.adraffy.ens.ENSNormalize
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.URLDecoder

data class QRPaymentRequest(
    override val to: String,
    override val amountEth: String?,
    override val chainId: Long,
) : PaymentRequest {

    /**
     * Converts request to an ERC-681 standard transaction request URI:
     * ethereum:0x123...[@421614][?value=0.01e18] see : https://eips.ethereum.org/EIPS/eip-681
     * for more info
     */
    fun toUri(): String {
        val builder = StringBuilder("ethereum:").append(to)

        if (chainId > 0) {
            builder.append("@").append(chainId)
        }

        if (!amountEth.isNullOrBlank()) {
            builder.append("?value=").append(amountEth).append("e18")
        }

        return builder.toString()
    }

    companion object {
        private val WEI_IN_ETH = BigDecimal("1000000000000000000")
        private val ETH_ADDRESS_REGEX = Regex("^0x[0-9a-fA-F]{40}$")

        /**
         * Parses transaction request URLs adhering to ERC-681 standard (e.g. ethereum:0x...@1?value=1e18),
         * legacy Voltic deep links (https://voltic-pay.github.io/pay?to=...&amount=...&chainId=...),
         * or bare Ethereum addresses / ENS names.
         */
        fun parse(
            rawText: String,
            defaultChainId: Long = ChainConfig.current.chainId
        ): QRPaymentRequest {
            val text = rawText.trim()
            if (text.isEmpty()) {
                throw IllegalArgumentException("QR code content is empty")
            }

            // ERC-681 URL format
            if (text.startsWith("ethereum:", ignoreCase = true)) {
                return parseErc681(text, defaultChainId)
            }

            // Legacy Voltic URL format
            if (text.startsWith("https://voltic-pay.github.io/pay", ignoreCase = true)) {
                return parseLegacyVolticUrl(text, defaultChainId)
            }

            // 3. Fallback: Bare Ethereum address or ENS name
            if (ETH_ADDRESS_REGEX.matches(text) || isValidEnsName(text)) {
                return QRPaymentRequest(
                    to = text,
                    amountEth = null,
                    chainId = defaultChainId
                )
            }

            throw IllegalArgumentException("Not a valid payment QR code or Ethereum URI")
        }

        private fun decode(str: String): String {
            return try {
                URLDecoder.decode(str, "UTF-8")
            } catch (_: Exception) {
                str
            }
        }

        private fun parseErc681(text: String, defaultChainId: Long): QRPaymentRequest {
            val body = when {
                text.startsWith(
                    "ethereum:pay-",
                    ignoreCase = true
                ) -> text.substring("ethereum:pay-".length)

                else -> text.substring("ethereum:".length)
            }

            val queryIndex = body.indexOf('?')
            val (targetAndPath, queryString) = if (queryIndex != -1) {
                body.substring(0, queryIndex) to body.substring(queryIndex + 1)
            } else {
                body to ""
            }

            val pathIndex = targetAndPath.indexOf('/')
            val (targetAndChain, functionName) = if (pathIndex != -1) {
                targetAndPath.substring(0, pathIndex) to targetAndPath.substring(pathIndex + 1)
            } else {
                targetAndPath to null
            }

            val atIndex = targetAndChain.indexOf('@')
            val (rawTarget, chainIdFromUri) = if (atIndex != -1) {
                val chainStr = targetAndChain.substring(atIndex + 1)
                targetAndChain.substring(0, atIndex) to chainStr.toLongOrNull()
            } else {
                targetAndChain to null
            }

            val targetAddress = decode(rawTarget).trim()
            if (targetAddress.isEmpty()) {
                throw IllegalArgumentException("Missing target address in Ethereum URI")
            }

            val paramsMap = mutableMapOf<String, String>()
            if (queryString.isNotBlank()) {
                for (param in queryString.split("&")) {
                    if (param.isBlank()) continue
                    val parts = param.split("=", limit = 2)
                    val key = decode(parts[0]).trim()
                    val value = if (parts.size > 1) decode(parts[1]).trim() else ""
                    if (key.isNotEmpty()) {
                        paramsMap[key] = value
                    }
                }
            }

            val recipient: String
            val rawAmount: String?

            if (functionName.equals("transfer", ignoreCase = true)) {
                recipient = paramsMap["address"] ?: paramsMap["to"] ?: targetAddress
                rawAmount = paramsMap["uint256"] ?: paramsMap["value"]
            } else {
                recipient = targetAddress
                rawAmount = paramsMap["value"]
            }

            if (!ETH_ADDRESS_REGEX.matches(recipient) && !isValidEnsName(recipient)) {
                throw IllegalArgumentException("Invalid recipient address or ENS name: $recipient")
            }

            val amountEth = rawAmount?.let { parseWeiToEth(it) }
            val finalChainId = chainIdFromUri ?: defaultChainId
            if (finalChainId <= 0) {
                throw IllegalArgumentException("Invalid or missing chain ID")
            }

            return QRPaymentRequest(
                to = recipient,
                amountEth = amountEth,
                chainId = finalChainId
            )
        }

        private fun parseLegacyVolticUrl(text: String, defaultChainId: Long): QRPaymentRequest {
            val queryMap = mutableMapOf<String, String>()
            try {
                val uri = URI(text)
                uri.rawQuery?.split("&")?.forEach { param ->
                    val parts = param.split("=", limit = 2)
                    if (parts.isNotEmpty()) {
                        val key = decode(parts[0])
                        val value = if (parts.size > 1) decode(parts[1]) else ""
                        queryMap[key] = value
                    }
                }
            } catch (_: Exception) {
                throw IllegalArgumentException("Invalid Voltic payment URL format")
            }

            val to = queryMap["to"].orEmpty().trim()
            val chainIdStr = queryMap["chainId"]

            if (!ETH_ADDRESS_REGEX.matches(to) && !isValidEnsName(to)) {
                throw IllegalArgumentException("Invalid recipient Ethereum address")
            }

            val chainId = chainIdStr?.toLongOrNull() ?: defaultChainId
            if (chainId <= 0) {
                throw IllegalArgumentException("Invalid or missing chain ID")
            }

            val amountEth = queryMap["amount"]?.ifBlank { null }

            return QRPaymentRequest(
                to = to,
                amountEth = amountEth,
                chainId = chainId
            )
        }

        private fun parseWeiToEth(weiStr: String): String? {
            if (weiStr.isBlank()) return null
            return try {
                val weiBD = BigDecimal(weiStr.trim())
                if (weiBD.compareTo(BigDecimal.ZERO) < 0) {
                    throw IllegalArgumentException("Amount cannot be negative")
                }
                val ethBD = weiBD.divide(WEI_IN_ETH, 18, RoundingMode.HALF_UP)
                val plain = ethBD.stripTrailingZeros().toPlainString()
                if (plain == "0") "0" else plain
            } catch (e: Exception) {
                if (e is IllegalArgumentException) throw e
                throw IllegalArgumentException("Invalid numeric amount: $weiStr")
            }
        }

        private fun isValidEnsName(name: String): Boolean {
            val trimmed = name.trim()
            if (!trimmed.contains(".") || trimmed.startsWith(".")) return false
            return try {
                ENSNormalize.ENSIP15.normalize(trimmed)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
