package com.voltic.app.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voltic.app.chain.ArbitrumClient
import com.voltic.app.ui.model.BalanceFormatter
import java.math.BigInteger
import androidx.compose.ui.graphics.Color
import com.voltic.app.settings.SpendLimitPreferences
import androidx.compose.runtime.getValue
@Composable
fun SpendLimitUsageCard(
    limitInfo: ArbitrumClient.SpendLimitInfo?,
    modifier: Modifier = Modifier
) {
    val periods = remember { listOf("Daily", "Weekly", "Monthly") }
    val isEnabled by SpendLimitPreferences.isEnabled.collectAsState()

    if (isEnabled) { // soft check


        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
              containerColor = Color.Transparent
            )
        ) {
            Column(
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 5.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (limitInfo != null && limitInfo.amount > BigInteger.ZERO) {
                    // Convert Wei to ETH
                    val limitEth = limitInfo.amount.toBigDecimal().scaleByPowerOfTen(-18)
                    val spentEth = limitInfo.spent.toBigDecimal().scaleByPowerOfTen(-18)

                    val usagePercent = if (limitEth > java.math.BigDecimal.ZERO) {
                        (spentEth.toDouble() / limitEth.toDouble()).coerceIn(0.0, 1.0)
                    } else 0.0

                    val usedPct = (usagePercent * 100).toInt()
                    val periodLabel = periods.getOrElse(limitInfo.period) { "Active" }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${BalanceFormatter.formatCrypto(limitEth)} $periodLabel ",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$usedPct% Quota Used",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    LinearProgressIndicator(
                        progress = { usagePercent.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                        color = if (usagePercent > 0.8) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .2f)
                    )
                } else {
                    Text(
                        text = "No spending limit set (Unlimited)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}