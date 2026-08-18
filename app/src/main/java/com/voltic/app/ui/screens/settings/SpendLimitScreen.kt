package com.voltic.app.ui.screens.settings

import com.voltic.app.ui.components.SpendLimitUsageCard
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voltic.app.R
import com.voltic.app.chain.ArbitrumClient
import com.voltic.app.chain.explorer.EthPriceCache
import com.voltic.app.settings.SpendLimitPreferences
import com.voltic.app.ui.components.AmountInputField
import com.voltic.app.ui.model.AmountInputSanitizer
import com.voltic.app.ui.model.BalanceFormatter
import com.voltic.app.wallet.WalletManager
import kotlinx.coroutines.launch
import java.math.BigInteger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendLimitScreen(
    walletManager: WalletManager,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val chain = remember { ArbitrumClient() }
    val spendLimitsEnabled by SpendLimitPreferences.isEnabled.collectAsStateWithLifecycle()
    val ethPriceUsd by EthPriceCache.priceUsd.collectAsStateWithLifecycle()

    var limitAmountInput by remember { mutableStateOf("") }
    var managementAmountInput by remember { mutableStateOf("") }
    var selectedPeriod by remember { mutableIntStateOf(0) }

    var isUpdating by remember { mutableStateOf(false) }
    var isFetching by remember { mutableStateOf(true) }

    var currentLimitInfo by remember { mutableStateOf<ArbitrumClient.SpendLimitInfo?>(null) }
    var vaultBalance by remember { mutableStateOf(BigInteger.ZERO) }

    val periods = listOf("Daily", "Weekly", "Monthly")
    var expanded by remember { mutableStateOf(false) }

    fun refreshData() {
        scope.launch {
            isFetching = true
            val credentials = walletManager.loadExistingWalletAsync()
            if (credentials != null) {
                currentLimitInfo = chain.getSpendLimitInfo(credentials.address)
                vaultBalance = chain.getVaultBalance(credentials.address)
            }
            isFetching = false
        }
    }

    LaunchedEffect(spendLimitsEnabled) {
        if (spendLimitsEnabled) {
            refreshData()
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Limit Control", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isUpdating) {
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
            // EXPERIMENTAL WARNING
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings_alert),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "this feature is expermintal and unstable expect upredictibale behavior",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ALWAYS-VISIBLE TOGGLE CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Use Spending Limit Capital",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = buildAnnotatedString {
                                append("Off: every payment sends straight from your wallet. On: you can load capital into an on-chain vault with spending limits, ")
                                withStyle(
                                    style = SpanStyle(
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                        fontWeight = FontWeight.Bold
                                    )
                                ) {
                                    append("usable from NFC and QR ONLY.")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = spendLimitsEnabled,
                        onCheckedChange = { SpendLimitPreferences.setEnabled(context, it) }
                    )
                }
            }

            if (spendLimitsEnabled) {
                // 1. SPENDING CAPITAL (VAULT) CARD
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Spending Capital", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                        val ethBalance = vaultBalance.toBigDecimal().scaleByPowerOfTen(-18)
                        Text(
                            text = BalanceFormatter.formatCrypto(ethBalance),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black
                        )
                        currentLimitInfo?.let { info ->
                            if (info.amount > BigInteger.ZERO) {
                                SpendLimitUsageCard(limitInfo = currentLimitInfo)
                            } else {
                                Text("No spending limit set (Unlimited)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))

                        // CUSTOM MANAGEMENT (IN/OUT) — USD toggle enabled, deposit/withdraw is a real payment-like action
                        AmountInputField(
                            value = managementAmountInput,
                            onValueChange = { managementAmountInput = it },
                            ethPriceUsd = ethPriceUsd,
                            label = "Transfer Amount (ETH)",
                            enabled = !isUpdating
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isUpdating = true
                                        try {
                                            val creds = walletManager.loadExistingWalletAsync()!!
                                            chain.depositToVault(creds, managementAmountInput)
                                            managementAmountInput = ""
                                            refreshData()
                                            Toast.makeText(context, "Capital Loaded!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Log.e("SpendLimitScreen", "Deposit failed", e)
                                            Toast.makeText(context, "Load Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally { isUpdating = false }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isUpdating && AmountInputSanitizer.isGreaterThanZero(managementAmountInput)
                            ) {
                                Text("Deposit")
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isUpdating = true
                                        try {
                                            val creds = walletManager.loadExistingWalletAsync()!!
                                            chain.withdrawFromVault(creds, managementAmountInput)
                                            managementAmountInput = ""
                                            refreshData()
                                            Toast.makeText(context, "Withdrawal Success!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Log.e("SpendLimitScreen", "Withdraw failed", e)
                                            Toast.makeText(context, "Withdraw Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally { isUpdating = false }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isUpdating && AmountInputSanitizer.isGreaterThanZero(managementAmountInput)
                            ) {
                                Text("Withdraw")
                            }
                        }
                    }
                }

                // 2. LIMIT SETTING SECTION
                Text("Limit Configuration", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Time Window Dropdown
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = periods[selectedPeriod],
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Time Window") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(16.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            periods.forEachIndexed { index, period ->
                                DropdownMenuItem(
                                    text = { Text(period) },
                                    onClick = {
                                        selectedPeriod = index
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Right: Limit Amount Input — deliberately ETH-only, no USD toggle.
                    // This defines an on-chain rule denominated in ETH; converting it to a
                    // fluctuating USD figure would misrepresent what's actually being set.
                    OutlinedTextField(
                        value = limitAmountInput,
                        onValueChange = { limitAmountInput = AmountInputSanitizer.sanitizeCryptoAmount(it, limitAmountInput) },
                        label = { Text("Limit (ETH)") },
                        modifier = Modifier.weight(1.5f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(16.dp),
                        placeholder = { Text("e.g. 0.05") }
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Note: Changing on-chain rules requires a small gas fee from your Pocket Wallet.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = {
                        scope.launch {
                            isUpdating = true
                            try {
                                val creds = walletManager.loadExistingWalletAsync()!!
                                chain.updateSpendLimit(creds, selectedPeriod, limitAmountInput.trim())
                                limitAmountInput = ""
                                refreshData()
                                Toast.makeText(context, "Limit Applied!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e("SpendLimitScreen", "Update limit failed", e)
                                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally { isUpdating = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isUpdating && AmountInputSanitizer.isGreaterThanZero(limitAmountInput)
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Update Spending Rules", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}