package com.voltic.app.ui.screens.main

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voltic.app.R
import com.voltic.app.ui.components.ConfirmationField
import com.voltic.app.ui.components.ConfirmationScreen
import com.voltic.app.wallet.WalletManager
import kotlinx.coroutines.launch
import org.web3j.crypto.MnemonicUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreWalletScreen(
    walletManager: WalletManager,
    onWalletRestored: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var seedInput by remember { mutableStateOf("") }
    var isRestoring by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showOverwriteConfirmation by remember { mutableStateOf(false) }
    var validCleanedSeed by remember { mutableStateOf("") }

    fun cleanAndNormalizeMnemonic(rawInput: String): String {
        return rawInput
            .lowercase()
            .map { char -> if (char in 'a'..'z') char else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    fun executeRestore(mnemonicToRestore: String) {
        scope.launch {
            isRestoring = true
            try {
                walletManager.restoreWalletFromMnemonicAsync(mnemonicToRestore)
                Toast.makeText(context, "Wallet restored successfully!", Toast.LENGTH_SHORT).show()
                onWalletRestored()
            } catch (e: Exception) {
                Log.e("RestoreWalletScreen", "Restore failed", e)
                errorMessage = "Failed to restore: ${e.message}"
            } finally {
                isRestoring = false
                showOverwriteConfirmation = false
            }
        }
    }

    if (showOverwriteConfirmation) {
        ConfirmationScreen(
            title = "Replace Existing Wallet?",
            warningText = "WARNING: Restoring a new seed phrase will permanently overwrite your active wallet on this device. Make sure your current phrase is backed up!",
            isDestructive = true,
            confirmLabel = "Overwrite Wallet",
            cancelLabel = "Go Back",
            fields = listOf(
                ConfirmationField("Action", "Restore from valid phrase"),
                ConfirmationField("Seed Preview", "${validCleanedSeed.take(15)}...")
            ),
            onConfirm = { executeRestore(validCleanedSeed) },
            onCancel = { showOverwriteConfirmation = false }
        )
    } else {
        Scaffold(
            topBar = {
                LargeTopAppBar(
                    title = { Text("Restore", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold) },
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
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "Enter Your Seed Phrase",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = "Paste or type your 12 or 24-word recovery phrase separated by spaces.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = seedInput,
                    onValueChange = {
                        seedInput = it
                        errorMessage = null
                    },
                    label = { Text("Seed Phrase") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    minLines = 5,
                    maxLines = 8,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        val cleaned = cleanAndNormalizeMnemonic(seedInput)
                        if (cleaned.isEmpty() || !MnemonicUtils.validateMnemonic(cleaned)) {
                            errorMessage = "That recovery phrase isn't valid. Check the spelling and word order."
                            return@Button
                        }
                        validCleanedSeed = cleaned
                        if (walletManager.hasExistingWallet()) {
                            showOverwriteConfirmation = true
                        } else {
                            executeRestore(cleaned)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(32.dp),
                    enabled = !isRestoring && seedInput.isNotBlank()
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp)
                    } else {
                        Text("Restore Wallet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}