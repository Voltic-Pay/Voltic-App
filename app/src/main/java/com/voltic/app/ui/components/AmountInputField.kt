package com.voltic.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.voltic.app.R
import com.voltic.app.ui.model.AmountInputSanitizer
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun AmountInputField(
    value: String,                     // canonical ETH amount — always ETH, owned by caller
    onValueChange: (String) -> Unit,   // ALWAYS receives an ETH amount, regardless of display mode
    ethPriceUsd: BigDecimal?,          // null = no toggle at all, behaves like a plain ETH field
    modifier: Modifier = Modifier,
    label: String = "Amount (ETH)",
    enabled: Boolean = true,
    placeholder: String? = null,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    var isUsdMode by remember { mutableStateOf(false) }

    // What's shown in the field, in whichever unit is active right now.
    // Recomputed from the canonical ETH `value` ONLY when the mode is switched —
    // this is what makes swap actually convert the number instead of just relabeling it.
    var displayText by remember(isUsdMode) {
        mutableStateOf(
            if (isUsdMode) {
                val eth = value.toBigDecimalOrNull()
                if (eth != null && ethPriceUsd != null) {
                    eth.multiply(ethPriceUsd).setScale(2, RoundingMode.HALF_UP).toPlainString()
                } else ""
            } else {
                value
            }
        )
    }

    val displayLabel = if (isUsdMode) "Amount (USD)" else label
    val displayPlaceholder = if (isUsdMode) "e.g. 25" else "e.g. 0.05"
    val hint = ethPriceUsd?.let { price ->
        val amount = displayText.toBigDecimalOrNull() ?: return@let null
        if (isUsdMode) {
            "≈ ${amount.divide(price, 8, RoundingMode.HALF_UP)} ETH"
        } else {
            "≈ $${amount.multiply(price).setScale(2, RoundingMode.HALF_UP)} USD"
        }
    }

    OutlinedTextField(
        value = displayText,

        onValueChange = { typed ->
            val sanitized = AmountInputSanitizer.sanitizeCryptoAmount(typed, displayText)
            displayText = sanitized

            // Whatever unit the user is typing in, always report the ETH equivalent upward.
            val ethAmount = if (isUsdMode) {
                val usd = sanitized.toBigDecimalOrNull()
                if (ethPriceUsd != null && ethPriceUsd > BigDecimal.ZERO && usd != null) {
                    usd.divide(ethPriceUsd, 18, RoundingMode.HALF_UP).toPlainString()
                } else {
                    ""
                }
            } else {
                sanitized
            }
            onValueChange(ethAmount)
        },
        label = { Text(displayLabel) },
        placeholder = {Text(displayPlaceholder) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        trailingIcon = if (ethPriceUsd != null) {
            {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(enabled = enabled) { isUsdMode = !isUsdMode }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(if (isUsdMode) "USD" else "ETH", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Icon(painter = painterResource(id = R.drawable.ic_swap), contentDescription = "Switch currency")
                    }
                }
            }
        } else null,
        supportingText = hint?.let { { Text(it) } }
    )
}