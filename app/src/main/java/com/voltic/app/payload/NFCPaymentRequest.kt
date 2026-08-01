package com.voltic.app.payload

data class NFCPaymentRequest(
    override val to: String,
    override val amountEth: String?,
    override val chainId: Long,
) : PaymentRequest {




     // encoding for the APDU data field. also diffrent than QR formating btw

    fun encode(): String {
        return listOf(to, amountEth.orEmpty(), chainId.toString()).joinToString("|")
    }

    companion object {
        private val ETH_ADDRESS_REGEX = Regex("^0x[0-9a-fA-F]{40}$")

        fun parse(raw: String): NFCPaymentRequest {
            val parts = raw.trim().split("|")
            if (parts.size != 3) {
                throw IllegalArgumentException("Malformed NFC payment payload")
            }

            val (to, amountEthRaw, chainIdStr) = parts

            if (!ETH_ADDRESS_REGEX.matches(to)) {
                throw IllegalArgumentException("Invalid recipient Ethereum address")
            }

            val chainId = chainIdStr.toLongOrNull() ?: -1L
            if (chainId <= 0) {
                throw IllegalArgumentException("Invalid or missing chain ID")
            }

            val amountEth = amountEthRaw.ifBlank { null }

            return NFCPaymentRequest(
                to = to,
                amountEth = amountEth,
                chainId = chainId,
            )
        }
    }
}