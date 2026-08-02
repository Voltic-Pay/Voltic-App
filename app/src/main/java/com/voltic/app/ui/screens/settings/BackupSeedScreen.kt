package com.voltic.app.ui.screens.settings

import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.voltic.app.R
import com.voltic.app.wallet.WalletManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSeedScreen(
    walletManager: WalletManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mnemonic by remember { mutableStateOf<String?>(null) }
    var isAuthenticated by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    val biometricPrompt = remember {
        val activity = context as FragmentActivity
        BiometricPrompt(activity, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                authError = "Authentication error: $errString"
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                isAuthenticated = true
                scope.launch {
                    try {
                        mnemonic = walletManager.getMnemonicForBackupAsync()
                    } catch (e: Exception) {
                        Log.e("BackupSeedScreen", "Failed to load mnemonic", e)
                        authError = "Failed to load seed."
                    }
                }
            }
        })
    }

    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authenticate to View Seed Phrase")
            .setSubtitle("Your 24-word recovery phrase is the only way to recover your wallet if you lose this device.")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
    }

    LaunchedEffect(Unit) {
        val biometricManager = BiometricManager.from(context)
        when (biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                biometricPrompt.authenticate(promptInfo)
            }
            else -> {
                isAuthenticated = true
                mnemonic = walletManager.getMnemonicForBackupAsync()
            }
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Backup Phrase", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 1f))
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings_alert),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            "Never Share This Phrase",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Anyone with these 24 words can take all your funds forever.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (mnemonic == null && authError == null) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (authError != null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(authError!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = {
                        authError = null
                        biometricPrompt.authenticate(promptInfo)
                    }) {
                        Text("Retry Authentication")
                    }
                }
            } else {
                mnemonic?.let { phrase ->
                    val words = phrase.split(" ")
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        words.chunked(2).forEachIndexed { rowIndex, rowWords ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowWords.forEachIndexed { colIndex, word ->
                                    val index = rowIndex * 2 + colIndex + 1
                                    SeedWordCard(
                                        index = index,
                                        word = word,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(2 - rowWords.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }


                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 1f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Recommended Backup Strategy:",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Write these words on paper and store it in two separate, safe locations. Do not take a screenshot or store it in your cloud.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeedWordCard(
    index: Int,
    word: String,
    modifier: Modifier = Modifier
) {
    // Converts dp to sp using device density. Ignores system font size settings.
    val fixedIndexSize = with(LocalDensity.current) { 12.dp.toSp() }
    val fixedWordSize = with(LocalDensity.current) { 15.dp.toSp() }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = index.toString(),
                fontSize = fixedIndexSize,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                maxLines = 1,textAlign = TextAlign.Center
            )
            Text(
                text = word,
                fontSize = fixedWordSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible ,

            )
        }
    }
}