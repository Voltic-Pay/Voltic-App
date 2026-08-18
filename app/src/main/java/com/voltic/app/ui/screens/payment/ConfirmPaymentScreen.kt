package com.voltic.app.ui.screens.payment

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.voltic.app.R
import com.voltic.app.chain.ArbitrumClient
import com.voltic.app.payload.PaymentRequest
import com.voltic.app.transport.nfc.NfcSession
import com.voltic.app.ui.components.StatusBanner
import com.voltic.app.wallet.WalletManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voltic.app.chain.explorer.EthPriceCache
import com.voltic.app.settings.SpendLimitPreferences
import com.voltic.app.ui.components.AmountInputField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmPaymentScreen(
    walletManager: WalletManager,
    paymentRequest: PaymentRequest,
    onPaymentSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val ethPriceUsd by EthPriceCache.priceUsd.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val chain = remember { ArbitrumClient() }
    val spendLimitsEnabled by SpendLimitPreferences.isEnabled.collectAsStateWithLifecycle()
    var customAmountInput by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var sendResult by remember { mutableStateOf<String?>(null) }
    var useVault by remember(spendLimitsEnabled) { mutableStateOf(spendLimitsEnabled) }

    val finalAmount = paymentRequest.amountEth ?: customAmountInput.trim()
    val isSuccess = sendResult?.startsWith("Success", ignoreCase = true) == true ||
            sendResult?.startsWith("Authorized", ignoreCase = true) == true

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Confirm", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isSending) {
                        Icon(painter = painterResource(id = R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Review Payment Details",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column {
                        Text("Recipient Address", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(paymentRequest.to, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Column {
                        Text("Network", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Arbitrum Sepolia (Chain ID ${paymentRequest.chainId})", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    if (paymentRequest.amountEth != null) {
                        Column {
                            Text("Amount to Send", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${paymentRequest.amountEth} ETH", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        Column {
                            Text("Enter Amount (ETH)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            AmountInputField(
                                value = customAmountInput,
                                onValueChange = { customAmountInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isSending && sendResult == null,
                                ethPriceUsd = ethPriceUsd
                            )
                        }
                    }

                    if (spendLimitsEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pay from Spending Limit Capital", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Apply spending limits", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = useVault, onCheckedChange = { useVault = it }, enabled = !isSending && sendResult == null)
                        }
                    }
                }
            }

            sendResult?.let { result ->
                StatusBanner(message = result)
            }

            Spacer(modifier = Modifier.weight(1f))

            if (isSuccess) {
                Button(
                    onClick = onPaymentSuccess,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Text(if (paymentRequest is com.voltic.app.payload.NFCPaymentRequest) "Ready (Return to Dashboard)" else "Done", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FilledTonalButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f).height(64.dp),
                        shape = RoundedCornerShape(32.dp),
                        enabled = !isSending
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.titleMedium)
                    }

                    Button(
                        onClick = {
                            if (finalAmount.isBlank()) {
                                Toast.makeText(context, "Please enter an amount to send", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            if (paymentRequest is com.voltic.app.payload.NFCPaymentRequest) {
                                NfcSession.updateAmount(finalAmount)
                                NfcSession.useVault = useVault
                                NfcSession.authorize()
                                sendResult = "Authorized! Tap Merchant's phone again to send."
                            } else {
                                scope.launch {
                                    isSending = true
                                    sendResult = null
                                    try {
                                        val credentials = walletManager.loadExistingWalletAsync()
                                            ?: throw IllegalStateException("No wallet loaded!")
                                        val txHash = withContext(Dispatchers.IO) {
                                            if (useVault) {
                                                chain.executeVaultPayment(credentials, paymentRequest.to, finalAmount)
                                            } else {
                                                chain.sendEth(credentials, paymentRequest.to, finalAmount)
                                            }
                                        }
                                        sendResult = "Success! Tx: $txHash"
                                    } catch (e: Exception) {
                                        Log.e("ConfirmPayment", "Payment failed", e)
                                        val displayMsg = ArbitrumClient.formatError(e.message)
                                        sendResult = "Payment Failed: $displayMsg"
                                    } finally {
                                        isSending = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(64.dp),
                        shape = RoundedCornerShape(32.dp),
                        enabled = !isSending && finalAmount.isNotBlank() && sendResult == null
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp)
                        } else {
                            Text("Confirm", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}