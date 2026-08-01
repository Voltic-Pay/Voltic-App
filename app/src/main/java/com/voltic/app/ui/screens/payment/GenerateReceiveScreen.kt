package com.voltic.app.ui.screens.payment

import android.app.Activity
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.voltic.app.R
import com.voltic.app.payload.NFCPaymentRequest
import com.voltic.app.payload.QRPaymentRequest
import com.voltic.app.transport.nfc.NfcReaderManager
import com.voltic.app.transport.nfc.ReaderState
import com.voltic.app.transport.qr.QrGenerator
import com.voltic.app.ui.components.StatusBanner
import com.voltic.app.ui.model.AmountInputSanitizer
import com.voltic.app.wallet.WalletManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.web3j.crypto.Credentials
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateReceiveScreen(
    currentAddress: String,
    walletManager: WalletManager,
    chainId: Long = 421614L,
    onBack: () -> Unit
) {
    val activity = LocalContext.current as Activity

    var amountInput by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var readerState by remember { mutableStateOf<ReaderState>(ReaderState.WaitingForTap1) }
    var nfcReader by remember { mutableStateOf<NfcReaderManager?>(null) }
    var merchantCredentials by remember { mutableStateOf<Credentials?>(null) }

    LaunchedEffect(Unit) {
        merchantCredentials = walletManager.loadExistingWalletAsync()
    }

    DisposableEffect(Unit) {
        onDispose {
            nfcReader?.stop()
        }
    }

    LaunchedEffect(currentAddress, amountInput, merchantCredentials) {
        val creds = merchantCredentials ?: return@LaunchedEffect

        delay(400.milliseconds)

        try {
            nfcReader?.stop()

            val nfcRequest = NFCPaymentRequest(
                to = currentAddress,
                amountEth = amountInput.ifBlank { null },
                chainId = chainId
            )

            val newReader = NfcReaderManager(activity, nfcRequest, creds) { state ->
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
                is ReaderState.ProcessingTap1 -> StatusBanner("Customer tapped! Fetching network data...")
                is ReaderState.WaitingForTap2 -> StatusBanner("Ready! Waiting for customer to authorize and Tap again...")
                is ReaderState.Broadcasting -> StatusBanner("Broadcasting Transaction to Arbitrum...")
                is ReaderState.Success -> StatusBanner("Payment Received! Tx: ${state.txHash}", isSuccess = true)
                is ReaderState.Error -> StatusBanner("Error: ${state.message}")
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

            OutlinedTextField(
                value = amountInput,
                onValueChange = { newValue ->
                    amountInput = AmountInputSanitizer.sanitizeCryptoAmount(input = newValue, fallback = amountInput)
                },
                label = { Text("Requested Amount (ETH)", style = MaterialTheme.typography.titleMedium) },
                placeholder = { Text("e.g. 0.005") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
        }
    }
}