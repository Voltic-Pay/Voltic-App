package com.voltic.app.ui.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

sealed interface BalanceUiState {
    object Loading : BalanceUiState

    data class Success(
        val ethAmount: BigDecimal,
        val ethPriceUsd: BigDecimal? = null
    ) : BalanceUiState {

        /**
         * Formats the ETH balance using the specialized BalanceFormatter.
         * Handles subscript notation for leading zeros.
         */
        val formatted: String
            get() = BalanceFormatter.formatCrypto(ethAmount)

        /**
         * Calculates and formats the total USD value based on current ETH price.
         * Returns e.g. "$1,234.56 USD" or null if price is unavailable.
         */
        val formattedUsd: String?
            get() {
                val price = ethPriceUsd ?: return null
                val usdValue = ethAmount.multiply(price).setScale(2, RoundingMode.HALF_UP)
                val formatter = DecimalFormat("$#,##0.00")
                return "${formatter.format(usdValue)} USD"
            }
    }

    data class Error(val message: String) : BalanceUiState
}