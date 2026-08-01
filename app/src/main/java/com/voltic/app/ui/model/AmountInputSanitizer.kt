package com.voltic.app.ui.model


import java.math.BigDecimal

object AmountInputSanitizer {
    fun sanitizeCryptoAmount(input: String, fallback: String): String {
        val sanitized = input.replace(" ", "").replace(",", ".")
        val isValid = sanitized.isEmpty() ||
                (sanitized.count { it == '.' } <= 1 && sanitized.all { it.isDigit() || it == '.' })

        return if (isValid) sanitized else fallback
    }

    fun isGreaterThanZero(input: String): Boolean {
        val amount = input.trim().toBigDecimalOrNull()
        return amount != null && amount > BigDecimal.ZERO
    }
}
