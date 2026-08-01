package com.voltic.app.ui.screens.main

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
import com.voltic.app.R
import com.voltic.app.ui.components.CopyButton
import com.voltic.app.wallet.WalletManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWalletScreen(
    walletManager: WalletManager,
    onWalletCreated: () -> Unit
) {
    val context = LocalContext.current

    var mnemonic by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(true) }
    var isSeedRevealed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val (_, seed) = walletManager.createNewWalletAsync()
            mnemonic = seed
        } catch (e: Exception) {
            Log.e("CreateWalletScreen", "Failed to create wallet", e)
            Toast.makeText(context, "Error creating wallet: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            isGenerating = false
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("New Wallet", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        if (isGenerating) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp), strokeWidth = 6.dp)
                    Text("Generating secure keys...", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "Backup Your Seed Phrase",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "⚠️ CRITICAL WARNING",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Save this 24-word seed phrase somewhere safe. If you lose it, your funds are lost forever. No one can recover it for you.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("24-Word Seed Phrase", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { isSeedRevealed = !isSeedRevealed }) {
                                    Icon(
                                        painter = painterResource(
                                            id = if (isSeedRevealed) R.drawable.ic_hide else R.drawable.ic_show
                                        ),
                                        contentDescription = if (isSeedRevealed) "Hide" else "Reveal",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                mnemonic?.let { seed ->
                                    CopyButton(
                                        textToCopy = seed,
                                        toastMessage = "Seed phrase copied!"
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isSeedRevealed) (mnemonic ?: "") else "••• ••• ••• ••• ••• ••• ••• •••\n••• ••• ••• ••• ••• ••• ••• •••\n••• ••• ••• ••• ••• ••• ••• •••",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = if (isSeedRevealed) FontWeight.Medium else FontWeight.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = onWalletCreated,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Text("I Have Saved My Seed Phrase", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}