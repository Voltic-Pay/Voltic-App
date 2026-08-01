package com.voltic.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voltic.app.R
import com.voltic.app.chain.explorer.TransactionRecord
import com.voltic.app.ui.model.BalanceFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionItem(
    transaction: TransactionRecord,
    currentAddress: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val isOutgoing = transaction.from.equals(currentAddress, ignoreCase = true)
    val amountEthRaw = transaction.value.toBigDecimalOrNull()?.movePointLeft(18) ?: java.math.BigDecimal.ZERO
    val amountEthFormatted = BalanceFormatter.formatCrypto(amountEthRaw, unit = "")

    val time = try {
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        sdf.format(Date(transaction.timeStamp.toLong() * 1000))
    } catch (_: Exception) {
        "Unknown time"
    }

    val icon = if (isOutgoing) R.drawable.ic_send else R.drawable.ic_receive
    val color = if (isOutgoing) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary

    val containerColor = if (isOutgoing)
        MaterialTheme.colorScheme.errorContainer else
        MaterialTheme.colorScheme.primaryContainer

    val onContainerColor = if (isOutgoing)
        MaterialTheme.colorScheme.onErrorContainer else
        MaterialTheme.colorScheme.onPrimaryContainer

    val partnerAddress = if (isOutgoing) transaction.to else transaction.from
    val displayPartner = if (partnerAddress.length > 10) {
        "${partnerAddress.take(6)}...${partnerAddress.takeLast(4)}"
    } else {
        partnerAddress
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Icon Container
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(20.dp),
            color = containerColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = if (isOutgoing) "OUT" else "IN",
                    modifier = Modifier.size(28.dp),
                    tint = onContainerColor
                )
            }
        }

        // Main Content Column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Top: Address / Partner Label
            Text(
                text = if (isOutgoing) "Sent to $displayPartner" else "Received from $displayPartner",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Bottom: Time on Left | Amount & Status on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(horizontalAlignment = Alignment.End) {

                    Text(
                        text = "${if (isOutgoing) "-" else "+"}$amountEthFormatted ETH",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (transaction.isError == "1") {
                        Text(
                            text = "Failed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}