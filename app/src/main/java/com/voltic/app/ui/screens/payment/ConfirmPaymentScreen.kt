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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voltic.app.R
import com.voltic.app.chain.ArbitrumClient
import com.voltic.app.chain.explorer.EthPriceCache
import com.voltic.app.payload.NFCPaymentRequest
import com.voltic.app.payload.PaymentRequest
import com.voltic.app.settings.SpendLimitPreferences
import com.voltic.app.transport.nfc.HandoffKind
import com.voltic.app.transport.nfc.NfcSession
import com.voltic.app.ui.components.AmountInputField
import com.voltic.app.ui.components.StatusBanner
import com.voltic.app.ui.model.AmountInputSanitizer
import com.voltic.app.ui.model.BalanceFormatter
import com.voltic.app.wallet.WalletManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.RoundingMode
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmPaymentScreen(
    walletManager: WalletManager,
    paymentRequest: PaymentRequest,
    onPaymentSuccess: () -> Unit,
    onBack: () -> Unit,
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
    var sentTxHash by remember { mutableStateOf<String?>(null) }

    // NFC sender side. After the user authorizes, the 2nd tap makes the HCE service sign and hand
    // the signed payload to the merchant's phone, which is the one that broadcasts it. So
    // "handed over" is NOT "paid": we only call it sent once the nonce we signed with has been
    // consumed on-chain. (The session disappearing only means *we* produced a response, it
    // doesn't prove the merchant's phone received it.)
    val handoff by NfcSession.handoff.collectAsStateWithLifecycle()
    var handoffConfirmed by remember { mutableStateOf(false) }
    var handoffExpired by remember { mutableStateOf(false) }

    LaunchedEffect(handoff) {
        val h = handoff ?: return@LaunchedEffect
        handoffConfirmed = false
        handoffExpired = false
        while (isActive) {
            try {
                val current = when (h.kind) {
                    HandoffKind.EOA -> chain.getAccountNonce(h.signerAddress)
                    HandoffKind.VAULT -> chain.getVaultNonceLong(h.signerAddress)
                }
                if (current > h.signedNonce) {
                    handoffConfirmed = true
                    break
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // offline / transient RPC error: keep waiting, that's the whole point of NFC pay
            }
            val expiresAt = h.expiresAtSec
            if (expiresAt != null && (System.currentTimeMillis() / 1000) > expiresAt) {
                handoffExpired = true
                break
            }
            delay(3.seconds)
        }
    }

    // Leaving without a signed handoff must also drop an already-authorized NFC session, otherwise
    // the next reader that taps this phone could still get a signature out of it.
    DisposableEffect(Unit) {
        onDispose {
            if (paymentRequest is NFCPaymentRequest && NfcSession.handoff.value == null) {
                NfcSession.clear()
            }
        }
    }

    val finalAmount = paymentRequest.amountEth ?: customAmountInput.trim()
    val finalAmountDecimal = finalAmount.toBigDecimalOrNull()
    val formattedEthText = finalAmountDecimal?.let { BalanceFormatter.formatCrypto(it) } ?: if (finalAmount.isNotBlank()) "$finalAmount ETH" else null
    val usdEquivalentText = if (finalAmountDecimal != null && ethPriceUsd != null) {
        "≈ $${finalAmountDecimal.multiply(ethPriceUsd).setScale(2, RoundingMode.HALF_UP)}"
    } else null

    val isSuccess = sendResult?.startsWith("Success", ignoreCase = true) == true ||
            sendResult?.startsWith("Authorized", ignoreCase = true) == true

    val sentHash = sentTxHash
    if (sentHash != null) {
        TransactionSentScreen(
            title = "Transaction sent!",
            amountText = formattedEthText,
            usdText = usdEquivalentText,
            detail = "To ${shortenHex(paymentRequest.to)}",
            footnote = "Tx ${shortenHex(sentHash)}",
            onDone = onPaymentSuccess,
        )
        return
    }

    val handedOff = handoff
    if (handedOff != null) {
        val style = when {
            handoffConfirmed -> SentScreenStyle.Success
            handoffExpired -> SentScreenStyle.Failed
            else -> SentScreenStyle.Pending
        }
        TransactionSentScreen(
            style = style,
            title = when (style) {
                SentScreenStyle.Success -> "Payment sent!"
                SentScreenStyle.Failed -> "Payment not completed"
                SentScreenStyle.Pending -> "Waiting for merchant"
            },
            amountText = formattedEthText,
            usdText = usdEquivalentText,
            detail = "To ${shortenHex(paymentRequest.to)}",
            footnote = when (style) {
                SentScreenStyle.Success -> "Confirmed on-chain."
                SentScreenStyle.Failed -> "It expired before the merchant broadcast it. Nothing was spent."
                SentScreenStyle.Pending -> "Signed and handed to the merchant's phone. It isn't paid until they broadcast it, this updates when it lands on-chain."
            },
            onDone = onPaymentSuccess,
        )
        return
    }

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
                fontWeight = FontWeight.ExtraBold,
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
                        Text("${ArbitrumClient.ARBITRUM_CHAIN_NAME} (Chain ID ${paymentRequest.chainId})", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    val reqAmount = paymentRequest.amountEth
                    if (reqAmount != null) {
                        Column {
                            Text("Amount to Send", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val parsed = reqAmount.toBigDecimalOrNull()
                                val formattedCrypto = parsed?.let { BalanceFormatter.formatCrypto(it) } ?: "$reqAmount ETH"
                                val usdVal = if (parsed != null && ethPriceUsd != null) {
                                    "≈ $${parsed.multiply(ethPriceUsd).setScale(2, RoundingMode.HALF_UP)}"
                                } else null

                                Text(
                                    text = formattedCrypto,
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                usdVal?.let { usd ->
                                    Text(
                                        text = usd,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 4.dp),
                                    )
                                }
                            }
                        }
                    } else {
                        Column {
                            Text("Enter Amount (ETH)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            AmountInputField(
                                value = customAmountInput,
                                onValueChange = { customAmountInput = AmountInputSanitizer.sanitizeCryptoAmount(it, customAmountInput) },
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
                StatusBanner(message = result, isSuccess = isSuccess)
            }

            Spacer(modifier = Modifier.weight(1f))

            if (isSuccess) {
                Button(
                    onClick = onPaymentSuccess,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Text(if (paymentRequest is NFCPaymentRequest) "Ready (Return to Dashboard)" else "Done", style = MaterialTheme.typography.labelLarge)
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

                            if (paymentRequest is NFCPaymentRequest) {
                                NfcSession.updateAmount(finalAmount)
                                NfcSession.useVault = useVault
                                NfcSession.authorize()
                                sendResult = "Authorized! Tap Merchant's phone again to send."
                            } else {
                                scope.launch {
                                    isSending = true
                                    sendResult = null
                                    try {
                                        val wallet = walletManager.loadExistingWalletAsync()
                                            ?: throw IllegalStateException("No wallet loaded!")
                                        val txHash = withContext(Dispatchers.IO) {
                                            if (useVault) {
                                                chain.executeVaultPayment(wallet, paymentRequest.to, finalAmount)
                                            } else {
                                                chain.sendEth(wallet, paymentRequest.to, finalAmount)
                                            }
                                        }
                                        sendResult = "Success! Tx: $txHash"
                                        sentTxHash = txHash
                                    } catch (e: Exception) {
                                        if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                                        Log.e("ConfirmPayment", "Payment failed", e)
                                        val displayMsg = ArbitrumClient.formatError(e)
                                        sendResult = if (ArbitrumClient.isNetworkError(e)) {
                                            "Payment Failed: $displayMsg (Network offline? Tap merchant's phone via NFC to pay offline.)"
                                        } else {
                                            "Payment Failed: $displayMsg"
                                        }
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

private fun shortenHex(value: String): String =
    if (value.length > 14) "${value.take(6)}…${value.takeLast(4)}" else value
