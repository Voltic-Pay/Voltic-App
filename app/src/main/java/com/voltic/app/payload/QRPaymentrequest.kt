package com.voltic.app.payload

import android.net.Uri

data class QRPaymentRequest(
    override val to: String,
    override val amountEth: String?,
    override val chainId: Long,
) : PaymentRequest {

    /**
     * Converts request to a deep link URI string that any Android camera can open:
     * https://voltic-pay.github.io/pay?to=0x123...&amount=0.01&chainId=421614
     * which will not work for now .... until implement it
     */
    fun toUri(): String {
        val builder = Uri.Builder()
            .scheme("https")
            .authority("voltic-pay.github.io")
            .path("pay")
            .appendQueryParameter("to", to)
            .appendQueryParameter("chainId", chainId.toString())

        if (amountEth != null) builder.appendQueryParameter("amount", amountEth)


        return builder.build().toString()
    }


    companion object {
        private val ETH_ADDRESS_REGEX = Regex("^0x[0-9a-fA-F]{40}$")

         // parse to https://voltic-pay.github.io/pay link format. which is completly random for now until i either deoply a code or get a proper domain

        fun parse(rawText: String): QRPaymentRequest {
            val text = rawText.trim()

            if (!text.startsWith("https://voltic-pay.github.io/pay", ignoreCase = true)) {
                throw IllegalArgumentException("Not a valid Voltic payment QR code")
            }

            val uri = Uri.parse(text)
            val to = uri.getQueryParameter("to") ?: ""
            val chainIdStr = uri.getQueryParameter("chainId") ?: ""

            if (!ETH_ADDRESS_REGEX.matches(to)) {
                throw IllegalArgumentException("Invalid recipient Ethereum address")
            }

            val chainId = chainIdStr.toLongOrNull() ?: -1L
            if (chainId <= 0) {
                throw IllegalArgumentException("Invalid or missing chain ID")
            }

            val amountEth = uri.getQueryParameter("amount")?.ifBlank { null }


            return QRPaymentRequest(
                to = to,
                amountEth = amountEth,
                chainId = chainId,
            )
        }
    }
}

