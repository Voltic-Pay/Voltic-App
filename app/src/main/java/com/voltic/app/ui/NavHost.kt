package com.voltic.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.voltic.app.payload.PaymentRequest
import com.voltic.app.payload.QRPaymentRequest
import com.voltic.app.transport.nfc.NfcSession
import com.voltic.app.ui.navigation.Screen
import com.voltic.app.ui.screens.settings.BackupSeedScreen
import com.voltic.app.ui.screens.payment.ConfirmPaymentScreen
import com.voltic.app.ui.screens.main.CreateWalletScreen
import com.voltic.app.ui.screens.main.DashboardScreen
import com.voltic.app.ui.screens.payment.GenerateReceiveScreen
import com.voltic.app.ui.screens.main.RestoreWalletScreen
import com.voltic.app.ui.screens.payment.ScanQrScreen
import com.voltic.app.ui.screens.settings.SpendLimitScreen
import com.voltic.app.ui.screens.main.TransactionHistoryScreen
import com.voltic.app.ui.screens.main.WelcomeScreen
import com.voltic.app.ui.theme.VolticTheme
import com.voltic.app.wallet.WalletManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────
// NAV RACE GUARD
// Kept in this same file (not a separate file) on purpose, for easy rollback.
// Problem: NavController.navigate() / popBackStack() fire unconditionally on
// every tap. If a second nav event arrives while the first transition is
// still resolving (very easy to trigger with a fast double-tap, e.g. right
// as a skeleton finishes loading), you can land on a transient, unattached
// state -> blank screen.
// Fix: only allow a nav action when the current back stack entry is fully
// RESUMED, i.e. no transition is in flight. If it isn't, the tap is simply
// dropped instead of colliding with the in-progress transition.
// ─────────────────────────────────────────────────────────────────────────
private fun NavController.navigateSafely(route: String, builder: NavOptionsBuilder.() -> Unit = {}) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        navigate(route, builder)
    }
}

private fun NavController.popBackStackSafely() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    }
}

@Composable
fun VolticApp(
    walletManager: WalletManager,
    deepLinkUri: String? = null,
    //onStartNfcReader: (NFCPaymentRequest, (ReaderState) -> Unit) -> Unit = { _, _ -> },
    //onStopNfcReader: () -> Unit = {} // turn out i don't need them
) {
    VolticTheme {
        val navController = rememberNavController()
        var hasWallet by remember { mutableStateOf<Boolean?>(null) }

        LaunchedEffect(Unit) {
            hasWallet = withContext(Dispatchers.IO) {
                walletManager.hasExistingWallet()
            }
        }

        if (hasWallet == null) {
            // Splash/Loading state to prevent blocking the Main thread with Keystore I/O
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {}
            return@VolticTheme
        }

        val startDestination = if (hasWallet == true) Screen.Dashboard.route else Screen.Welcome.route

        var pendingPaymentRequest by remember { mutableStateOf<PaymentRequest?>(null) }

        // Wrap the entire navigation in a Surface to provide a consistent background color
        // This prevents the "white flash" during transitions in dark mode.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            // Case 1: external camera/Lens deep link
            LaunchedEffect(deepLinkUri) {
                if (!deepLinkUri.isNullOrBlank()) {
                    try {
                        pendingPaymentRequest = QRPaymentRequest.parse(deepLinkUri)
                        navController.navigate(Screen.ConfirmPayment.route)
                    } catch (_: Exception) {
                        // TODO:Invalid link
                    }
                }
            }

            // Case 2: NFC service pushed a request
            // NOTE: NfcSession.pendingRequest re-emits when the customer fills in an
            // amount mid-session (see NfcSession.updateAmount), not just when a brand
            // new tap starts a session. We only want to *navigate* on the first,
            // genuinely-new request (null -> request). A later re-emission for the
            // same in-progress session should just refresh the already-visible
            // screen's data, not push a second ConfirmPayment on top of it.
            val incomingNfcRequest by NfcSession.pendingRequest.collectAsStateWithLifecycle()
            var lastHandledNfcRequest by remember { mutableStateOf<com.voltic.app.payload.NFCPaymentRequest?>(null) }
            LaunchedEffect(incomingNfcRequest) {
                val request = incomingNfcRequest
                if (request != null) {
                    pendingPaymentRequest = request
                    if (lastHandledNfcRequest == null) {
                        // genuinely new session -> navigate
                        navController.navigate(Screen.ConfirmPayment.route) {
                            launchSingleTop = true
                        }
                    }
                    // else: same session, amount/flags updated -> data update only, no nav
                }
                lastHandledNfcRequest = request
            }

            NavHost(
                navController = navController,
                startDestination = startDestination
            ) {
                composable(Screen.Welcome.route) {
                    WelcomeScreen(
                        onCreateNew = { navController.navigateSafely(Screen.CreateWallet.route) },
                        onRestore = { navController.navigateSafely(Screen.RestoreWallet.route) }
                    )
                }

                composable(Screen.CreateWallet.route) {
                    CreateWalletScreen(
                        walletManager = walletManager,
                        onWalletCreated = {
                            navController.navigateSafely(Screen.Dashboard.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.RestoreWallet.route) {
                    RestoreWalletScreen(
                        walletManager = walletManager,
                        onWalletRestored = {
                            navController.navigateSafely(Screen.Dashboard.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStackSafely() }
                    )
                }

                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        walletManager = walletManager,
                        onSwitchWallet = { navController.navigateSafely(Screen.RestoreWallet.route) },
                        onNavigateToScanQr = { navController.navigateSafely(Screen.ScanQr.route) },
                        onNavigateToGenerateQr = { navController.navigateSafely(Screen.GenerateQr.route) },
                        onNavigateToHistory = { navController.navigateSafely(Screen.TransactionHistory.route) },
                        onNavigateToBackupSeed = { navController.navigateSafely(Screen.BackupSeed.route) },
                        onNavigateToLimits = { navController.navigateSafely(Screen.SpendLimit.route) }

                    )
                }

                composable(Screen.BackupSeed.route) {
                    BackupSeedScreen(
                        walletManager = walletManager,
                        onBack = { navController.popBackStackSafely() }
                    )
                }

                composable(Screen.SpendLimit.route) {
                    SpendLimitScreen(
                        walletManager = walletManager,
                        onBack = { navController.popBackStackSafely() }
                    )
                }
                composable(Screen.TransactionHistory.route) {
                    TransactionHistoryScreen(
                        onBack = { navController.popBackStackSafely() }
                    )
                }

                composable(Screen.GenerateQr.route) {
                    var activeAddress by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(Unit) {
                        val credentials = walletManager.loadExistingWalletAsync()
                        activeAddress = credentials?.address
                    }

                    activeAddress?.let { address ->
                        GenerateReceiveScreen(
                            walletManager = walletManager,
                            currentAddress = address,
                            onBack = { navController.popBackStackSafely() }
                        )
                    }
                }

                composable(Screen.ScanQr.route) {
                    ScanQrScreen(
                        onPaymentScanned = { paymentRequest ->
                            pendingPaymentRequest = paymentRequest
                            navController.navigateSafely(Screen.ConfirmPayment.route) {
                                popUpTo(Screen.ScanQr.route) { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStackSafely() }
                    )
                }

                composable(Screen.ConfirmPayment.route) {
                    pendingPaymentRequest?.let { request ->
                        ConfirmPaymentScreen(
                            walletManager = walletManager,
                            paymentRequest = request,
                            onPaymentSuccess = {
                                pendingPaymentRequest = null
                                NfcSession.clear()
                                navController.navigateSafely(Screen.Dashboard.route) {
                                    popUpTo(Screen.Dashboard.route) { inclusive = true }
                                }
                            },
                            onBack = {
                                pendingPaymentRequest = null
                                navController.popBackStackSafely()
                            }
                        )
                    }
                }
            }
        }
    }
}