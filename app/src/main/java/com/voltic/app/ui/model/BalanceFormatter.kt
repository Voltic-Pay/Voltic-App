package com.voltic.app.ui.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

object BalanceFormatter {

    /**
     * Converts a number of leading zeros to Unicode subscripts.
     * Example: 5 -> "₅"
     */
    private fun toSubscript(count: Int): String {
        val countStr = count.toString()
        val builder = StringBuilder()
        for (char in countStr) {
            builder.append(when (char) {
                '0' -> "\u2080"
                '1' -> "\u2081"
                '2' -> "\u2082"
                '3' -> "\u2083"
                '4' -> "\u2084"
                '5' -> "\u2085"
                '6' -> "\u2086"
                '7' -> "\u2087"
                '8' -> "\u2088"
                '9' -> "\u2089"
                else -> char
            })
        }
        return builder.toString()
    }

    /**
     * Formats a crypto amount with special subscript notation for small values (dust).
     * Example: 0.00000123 -> 0.0₅123
     */
    fun formatCrypto(amount: BigDecimal, unit: String = "ETH"): String {
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return "0 $unit"
        }

        val absAmount = amount.abs()
        val isZeroLeftOfDecimal = absAmount < BigDecimal.ONE

        if (!isZeroLeftOfDecimal) {
            // Standard formatting for > 1: max 3 decimals
            val formatter = DecimalFormat("#,##0.###").apply {
                roundingMode = RoundingMode.DOWN
            }
            return "${formatter.format(amount)} $unit"
        }

        // Small value logic (< 1)
        val stripped = absAmount.stripTrailingZeros()
        val leadingZeros = (stripped.scale() - stripped.precision()).coerceAtLeast(0)

        return if (leadingZeros >= 3) {
            // Use subscript notation: 0.0{count}{remaining}
            // leadingZeros is total zeros after dot. 
            // 0.00000123 -> leadingZeros = 5.
            // We show 0.0, then subscript 5, then significant digits.
            val significantPart = stripped.unscaledValue().toString()
            val result = "0.0${toSubscript(leadingZeros)}${significantPart.take(4)}"
            "$result $unit"
        } else {
            // Standard small value: leadingZeros + 4 digits
            val fractionDigits = leadingZeros + 4
            val formatter = DecimalFormat("0.0").apply {
                maximumFractionDigits = fractionDigits
                roundingMode = RoundingMode.DOWN
            }
            "${formatter.format(amount)} $unit"
        }
    }
}
