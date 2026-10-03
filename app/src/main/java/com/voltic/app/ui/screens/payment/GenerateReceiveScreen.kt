package com.voltic.app.ui.screens.payment

import android.app.Activity
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voltic.app.R
import com.voltic.app.chain.ArbitrumClient
import com.voltic.app.chain.explorer.EthPriceCache
import com.voltic.app.payload.NFCPaymentRequest
import com.voltic.app.payload.QRPaymentRequest
import com.voltic.app.transport.nfc.NfcReaderManager
import com.voltic.app.transport.nfc.ReaderState
import com.voltic.app.transport.qr.QrGenerator
import com.voltic.app.ui.components.AmountInputField
import com.voltic.app.ui.components.StatusBanner
import com.voltic.app.ui.model.AmountInputSanitizer
import com.voltic.app.ui.model.BalanceFormatter
import com.voltic.app.wallet.WalletManager
import io.ethers.signers.PrivateKeySigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private data class ReceivedPayment(val amountEth: String?, val txHash: String?)

private fun parseEthToWei(input: String): BigInteger? = try {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) null
    else BigDecimal(trimmed).movePointRight(18).toBigInteger().takeIf { it.signum() > 0 }
} catch (_: NumberFormatException) {
    null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateReceiveScreen(
    currentAddress: String,
    walletManager: WalletManager,
    chainId: Long = ArbitrumClient.ARBITRUM_CHAIN_ID,
    onBack: () -> Unit,
) {
    val activity = LocalContext.current as Activity
    val ethPriceUsd by EthPriceCache.priceUsd.collectAsStateWithLifecycle()
    var amountInput by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var readerState by remember { mutableStateOf<ReaderState>(ReaderState.WaitingForTap1) }
    var nfcReader by remember { mutableStateOf<NfcReaderManager?>(null) }
    val currentNfcReader by rememberUpdatedState(nfcReader)
    var merchantWallet by remember { mutableStateOf<PrivateKeySigner?>(null) }

    LaunchedEffect(Unit) {
        merchantWallet = walletManager.loadExistingWalletAsync()
    }

    val chain = remember { ArbitrumClient() }
    var received by remember { mutableStateOf<ReceivedPayment?>(null) }
    val latestAmountInput by rememberUpdatedState(amountInput)

    // NFC path: this phone broadcasts the transaction itself, so we already know it landed.
    LaunchedEffect(readerState) {
        val state = readerState
        if ((state is ReaderState.Success) && (received == null)) {
            received = ReceivedPayment(amountEth = amountInput.ifBlank { null }, txHash = state.txHash)
        }
    }

    // QR / manual path: the payer broadcasts, we never see the transaction. Watch our own
    // balance instead. This also catches vault payments (internal ETH transfers that never show
    // up as a transaction "to" this address). If an amount was entered, the balance must rise
    // by at least that amount; otherwise any increase counts.
    LaunchedEffect(currentAddress) {
        var baseline: BigInteger? = null
        while (isActive && received == null) {
            try {
                val balance = chain.getBalanceWei(currentAddress)
                val base = baseline
                if (base == null) {
                    baseline = balance
                } else {
                    val delta = balance - base
                    val expected = parseEthToWei(latestAmountInput)
                    if (delta.signum() > 0 && (expected == null || delta >= expected)) {
                        received = ReceivedPayment(
                            amountEth = BigDecimal(delta).movePointLeft(18).stripTrailingZeros().toPlainString(),
                            txHash = null,
                        )
                    } else if (balance < base) {
                        baseline = balance // we spent something meanwhile; re-baseline
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // transient RPC/network error: just try again on the next tick
            }
            delay(2.seconds)
        }
    }

    LaunchedEffect(received) {
        if (received != null) nfcReader?.stop()
    }

    DisposableEffect(Unit) {
        onDispose {
            currentNfcReader?.stop()
        }
    }

    LaunchedEffect(currentAddress, amountInput, merchantWallet) {
        val wallet = merchantWallet ?: return@LaunchedEffect

        delay(400.milliseconds)

        try {
            nfcReader?.stop()

            val nfcRequest = NFCPaymentRequest(
                to = currentAddress,
                amountEth = amountInput.ifBlank { null },
                chainId = chainId
            )

            val newReader = NfcReaderManager(activity, nfcRequest, wallet) { state ->
                readerState = state
            }
            newReader.start()
            nfcReader = newReader

            val qrRequest = QRPaymentRequest(
                to = currentAddress,
                amountEth = amountInput.ifBlank { null },
                chainId = chainId
            )

            val bitmap = withContext(Dispatchers.Default) {
                QrGenerator.generateBitmap(qrRequest.toUri(), sizePx = 512)
            }
            qrBitmap = bitmap

        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("GenerateReceiveScreen", "Failed to setup reader/QR", e)
            qrBitmap = null
        }
    }

    val payment = received
    if (payment != null) {
        val parsedAmount = payment.amountEth?.toBigDecimalOrNull()
        val formattedCrypto = parsedAmount?.let { "+" + BalanceFormatter.formatCrypto(it) }
        val usdEquivalent = if ((parsedAmount != null) && (ethPriceUsd != null)) {
            "≈ $${parsedAmount.multiply(ethPriceUsd).setScale(2, RoundingMode.HALF_UP)}"
        } else null

        TransactionSentScreen(
            title = "Payment received!",
            amountText = formattedCrypto,
            usdText = usdEquivalent,
            detail = "${currentAddress.take(6)}...${currentAddress.takeLast(4)}",
            footnote = payment.txHash?.let { "Tx ${it.take(8)}…${it.takeLast(6)}" },
            onDone = onBack,
        )
        return
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Receive", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            when (val state = readerState) {
                is ReaderState.ProcessingTap1 -> StatusBanner("Customer tapped! Fetching network data...", isSuccess = true)
                is ReaderState.WaitingForTap2 -> StatusBanner("Ready! Waiting for customer to authorize and Tap again...", isSuccess = true)
                is ReaderState.Broadcasting -> StatusBanner("Broadcasting Transaction to ${ArbitrumClient.ARBITRUM_CHAIN_NAME}...", isSuccess = true)
                is ReaderState.Success -> StatusBanner("Payment Received! Tx: ${state.txHash}", isSuccess = true)
                is ReaderState.Error -> StatusBanner("Error: ${state.message}", isSuccess = false)
                is ReaderState.WaitingForTap1 -> { }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    qrBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Payment QR Code",
                            modifier = Modifier
                                .size(260.dp)
                                .padding(8.dp)
                        )
                    } ?: CircularProgressIndicator(modifier = Modifier.size(64.dp), strokeWidth = 6.dp)

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Scan QR or Tap NFC",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${currentAddress.take(6)}...${currentAddress.takeLast(4)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AmountInputField(
                value = amountInput,
                onValueChange = { newValue ->
                    amountInput = AmountInputSanitizer.sanitizeCryptoAmount(input = newValue, fallback = amountInput)
                },
                ethPriceUsd = ethPriceUsd,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
        }
    }
}
