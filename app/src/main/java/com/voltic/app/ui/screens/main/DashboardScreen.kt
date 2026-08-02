package com.voltic.app.ui.screens.main

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.voltic.app.R
import com.voltic.app.chain.ArbitrumClient
import com.voltic.app.chain.explorer.ExplorerClient
import com.voltic.app.chain.explorer.TransactionRecord
import com.voltic.app.ui.components.BalanceSkeleton
import com.voltic.app.ui.components.CopyButton
import com.voltic.app.ui.components.SettingsMenuContent
import com.voltic.app.ui.components.StatusBanner
import com.voltic.app.ui.components.TransactionItem
import com.voltic.app.ui.components.TransactionSkeletonItem
import com.voltic.app.ui.model.AmountInputSanitizer
import com.voltic.app.ui.model.BalanceUiState
import com.voltic.app.wallet.WalletManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DashboardAction { NONE, SEND_MANUAL }

// Single source of truth for the Send-panel motion timing. Deterministic
// tween durations (not spring()) on purpose: spring() approaches its target
// asymptotically and never mathematically "arrives," so Compose has to
// force-snap the last bit once a completion threshold is hit. That produces
// a visible "settle, pause, snap" artifact on close. tween has a fixed,
// predictable finish frame, so there's nothing left to snap.
private const val SEND_PANEL_ENTER_DURATION_MS = 220
private const val SEND_PANEL_EXIT_DURATION_MS = 180
private val SEND_PANEL_ELEVATION_EXPANDED = 8.dp
private val SEND_PANEL_ELEVATION_COLLAPSED = 0.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    walletManager: WalletManager,
    onSwitchWallet: () -> Unit,
    onNavigateToScanQr: () -> Unit,
    onNavigateToGenerateQr: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToBackupSeed: () -> Unit,
    onNavigateToLimits: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val chain = remember { ArbitrumClient() }
    val explorer = remember { ExplorerClient() }

    var limitInfo by remember { mutableStateOf<ArbitrumClient.SpendLimitInfo?>(null) }
    var address by remember { mutableStateOf<String?>(null) }
    var balanceState by remember { mutableStateOf<BalanceUiState>(BalanceUiState.Loading) }
    var transactions by remember { mutableStateOf<List<TransactionRecord>>(emptyList()) }
    var isInitialLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isBalanceVisible by remember { mutableStateOf(!walletManager.isBalanceHidden()) }

    var activeAction by remember { mutableStateOf(DashboardAction.NONE) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val pullToRefreshState = rememberPullToRefreshState()

    var recipientInput by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var isSendingTx by remember { mutableStateOf(false) }
    var sendTxResult by remember { mutableStateOf<String?>(null) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    fun refreshData(walletAddress: String, showSkeleton: Boolean = false) {
        scope.launch {
            if (showSkeleton) isInitialLoading = true
            isRefreshing = true

            try {
                val balanceDeferred = async(Dispatchers.IO) { chain.getBalance(walletAddress) }
                val historyDeferred = async(Dispatchers.IO) { explorer.getNormalTransactions(walletAddress) }
                val priceDeferred = async(Dispatchers.IO) { explorer.getEthPrice() }

                val weiBigInt = balanceDeferred.await()
                val history = historyDeferred.await()
                val price = priceDeferred.await()

                val ethDecimal = weiBigInt.toBigDecimal().scaleByPowerOfTen(-18)
                balanceState = BalanceUiState.Success(ethDecimal, price)
                transactions = history
            } catch (_: Exception) {
                if (balanceState !is BalanceUiState.Success) balanceState = BalanceUiState.Error("Network error")
            } finally {
                isInitialLoading = false
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val credentials = walletManager.loadExistingWalletAsync()
        if (credentials != null) {
            address = credentials.address
            refreshData(credentials.address, showSkeleton = true)
            limitInfo = chain.getSpendLimitInfo(credentials.address)
        } else {
            balanceState = BalanceUiState.Error("No active wallet found")
            isInitialLoading = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SettingsMenuContent(
                onShowSeed = onNavigateToBackupSeed,
                onSwitchWallet = onSwitchWallet,
                onNavigateToLimits = onNavigateToLimits,
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_launcher_monochrome),
                            contentDescription = "Voltic",
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_menu),
                                contentDescription = "Menu",
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    actions = {
                        IconButton(onClick = onNavigateToHistory) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_history),
                                contentDescription = "History",
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            }
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { address?.let { refreshData(it) } },
                state = pullToRefreshState,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. HERO BALANCE SECTION
                    if (isInitialLoading) {
                        BalanceSkeleton()
                    } else {
                        BalanceCard(
                            label = "Wallet Balance",
                            state = balanceState,
                            isVisible = isBalanceVisible,
                            onToggleVisibility = {
                                isBalanceVisible = !isBalanceVisible
                                walletManager.setBalanceHidden(!isBalanceVisible)
                            }
                        )

                        // Address Chip
                        address?.let { currentAddr ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                shape = CircleShape,
                                modifier = Modifier.padding(top = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val shortAddr = if (currentAddr.length > 13) {
                                        "${currentAddr.take(8)}...${currentAddr.takeLast(5)}"
                                    } else {
                                        currentAddr
                                    }
                                    Text(
                                        text = shortAddr,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    CopyButton(textToCopy = currentAddr, toastMessage = "Address copied!")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. QUICK ACTION GRID
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ActionButton(
                            label = "Send",
                            icon = painterResource(id = R.drawable.ic_send),
                            isSelected = activeAction == DashboardAction.SEND_MANUAL,
                            showDropdown = true,
                            onClick = {
                                activeAction = if (activeAction == DashboardAction.SEND_MANUAL) {
                                    DashboardAction.NONE
                                } else {
                                    DashboardAction.SEND_MANUAL
                                }
                            }
                        )

                        ActionButton(
                            label = "Receive",
                            icon = painterResource(id = R.drawable.ic_receive),
                            isSelected = false,
                            onClick = onNavigateToGenerateQr
                        )
                    }

                    // 3. SEND TRANSACTION PANEL
                    SendPanel(
                        visible = activeAction == DashboardAction.SEND_MANUAL,
                        amountInput = amountInput,
                        onAmountChange = { newValue ->
                            amountInput = AmountInputSanitizer.sanitizeCryptoAmount(input = newValue, fallback = amountInput)
                            sendTxResult = null
                        },
                        recipientInput = recipientInput,
                        onRecipientChange = {
                            recipientInput = it
                            sendTxResult = null
                        },
                        isSendingTx = isSendingTx,
                        sendTxResult = sendTxResult,
                        onScanQrClick = onNavigateToScanQr,
                        onConfirmSend = {
                            scope.launch {
                                isSendingTx = true
                                sendTxResult = null
                                try {
                                    if (!AmountInputSanitizer.isGreaterThanZero(amountInput)) {
                                        throw IllegalArgumentException("Please enter an amount more than 0")
                                    }
                                    val credentials = walletManager.loadExistingWalletAsync()
                                        ?: throw IllegalStateException("No active wallet loaded")
                                    val txHash = withContext(Dispatchers.IO) {
                                        chain.sendEth(credentials, recipientInput.trim(), amountInput.trim())
                                    }
                                    sendTxResult = "Success! Tx: $txHash"
                                    address?.let { refreshData(it) }
                                } catch (e: Exception) {
                                    Log.e("DashboardScreen", "Send ETH failed", e)
                                    sendTxResult = "Send Failed: ${e.message}"
                                } finally {
                                    isSendingTx = false
                                }
                            }
                        }
                    )

                    // 4. RECENT ACTIVITY
                    Text("Recent Activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (isInitialLoading) {
                        repeat(5) { TransactionSkeletonItem() }
                    } else if (transactions.isEmpty()) {
                        OutlinedCard(modifier = Modifier.fillMaxWidth().height(100.dp), shape = RoundedCornerShape(24.dp)) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No recent transactions found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                transactions.take(4).forEachIndexed { index, tx ->
                                    TransactionItem(transaction = tx, currentAddress = address ?: "")
                                    if (index != 3 && index != transactions.size - 1) {
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * The collapsible "Send ETH" panel.
 *
 * Motion notes (why this looks different from a naive AnimatedVisibility +
 * ElevatedCard):
 * - Uses tween(), not spring(), for both fade and size — deterministic finish,
 *   no asymptotic "settle then snap."
 * - Elevation is NOT a static Card property here; it's animated in lockstep
 *   with visibility via animateDpAsState, starting at 0dp. A plain
 *   ElevatedCard's shadow otherwise renders at full strength the instant the
 *   Card enters composition, ahead of the fade/expand still animating around
 *   it, which reads as the shadow "popping in" first.
 */
@Composable
private fun SendPanel(
    visible: Boolean,
    amountInput: String,
    onAmountChange: (String) -> Unit,
    recipientInput: String,
    onRecipientChange: (String) -> Unit,
    isSendingTx: Boolean,
    sendTxResult: String?,
    onScanQrClick: () -> Unit,
    onConfirmSend: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(SEND_PANEL_ENTER_DURATION_MS)) +
                expandVertically(animationSpec = tween(SEND_PANEL_ENTER_DURATION_MS), expandFrom = Alignment.Top),
        exit = fadeOut(animationSpec = tween(SEND_PANEL_EXIT_DURATION_MS)) +
                shrinkVertically(animationSpec = tween(SEND_PANEL_EXIT_DURATION_MS), shrinkTowards = Alignment.Top)
    ) {
        val cardElevation by animateDpAsState(
            targetValue = if (visible) SEND_PANEL_ELEVATION_EXPANDED else SEND_PANEL_ELEVATION_COLLAPSED,
            animationSpec = tween(SEND_PANEL_ENTER_DURATION_MS),
            label = "sendPanelElevation"
        )

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = cardElevation)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Send ETH", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

                    Surface(
                        onClick = onScanQrClick,
                        modifier = Modifier.size(width = 80.dp, height = 56.dp),
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 24.dp, bottomEnd = 12.dp, bottomStart = 12.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_scan),
                                contentDescription = "Scan QR",
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = onAmountChange,
                    label = { Text("Amount (ETH)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(16.dp)
                )

                OutlinedTextField(
                    value = recipientInput,
                    onValueChange = onRecipientChange,
                    label = { Text("Recipient Address or ENS") },
                    placeholder = { Text("0x... or name.eth") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                Button(
                    onClick = onConfirmSend,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isSendingTx && recipientInput.isNotBlank() && amountInput.isNotBlank()
                ) {
                    if (isSendingTx) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Text("Confirm & Send", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                sendTxResult?.let { result -> StatusBanner(message = result) }
            }
        }
    }
}

@Composable
fun BalanceCard(label: String, state: BalanceUiState, isVisible: Boolean, onToggleVisibility: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                when (state) {
                    is BalanceUiState.Success -> {
                        val ethText = if (isVisible) state.formatted else "***** ETH"
                        Text(text = ethText, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
                        Text(text = if (isVisible) state.formattedUsd ?: "" else "*** USD", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    else -> Text("...")
                }
            }
            FilledTonalIconButton(onClick = onToggleVisibility, modifier = Modifier.size(56.dp)) {
                Icon(painter = painterResource(id = if (isVisible) R.drawable.ic_hide else R.drawable.ic_show), contentDescription = null)
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, icon: Painter, isSelected: Boolean, showDropdown: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(contentAlignment = Alignment.BottomEnd) {
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.size(76.dp),
                shape = RoundedCornerShape(24.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(painter = icon, contentDescription = label, modifier = Modifier.size(32.dp))
            }
            if (showDropdown) {
                Surface(modifier = Modifier.size(28.dp).offset(x = 6.dp, y = 6.dp), shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painter = painterResource(id = if (isSelected) R.drawable.ic_up_dropdown else R.drawable.ic_dropdown), contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}
