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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            val incomingNfcRequest by NfcSession.pendingRequest.collectAsStateWithLifecycle()
            LaunchedEffect(incomingNfcRequest) {
                incomingNfcRequest?.let { request ->
                    pendingPaymentRequest = request
                    navController.navigate(Screen.ConfirmPayment.route)
                }
            }

            NavHost(
                navController = navController,
                startDestination = startDestination
            ) {
                composable(Screen.Welcome.route) {
                    WelcomeScreen(
                        onCreateNew = { navController.navigate(Screen.CreateWallet.route) },
                        onRestore = { navController.navigate(Screen.RestoreWallet.route) }
                    )
                }

                composable(Screen.CreateWallet.route) {
                    CreateWalletScreen(
                        walletManager = walletManager,
                        onWalletCreated = {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.RestoreWallet.route) {
                    RestoreWalletScreen(
                        walletManager = walletManager,
                        onWalletRestored = {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        walletManager = walletManager,
                        onSwitchWallet = { navController.navigate(Screen.RestoreWallet.route) },
                        onNavigateToScanQr = { navController.navigate(Screen.ScanQr.route) },
                        onNavigateToGenerateQr = { navController.navigate(Screen.GenerateQr.route) },
                        onNavigateToHistory = { navController.navigate(Screen.TransactionHistory.route) },
                        onNavigateToBackupSeed = { navController.navigate(Screen.BackupSeed.route) },
                        onNavigateToLimits = { navController.navigate(Screen.SpendLimit.route) }

                    )
                }

                composable(Screen.BackupSeed.route) {
                    BackupSeedScreen(
                        walletManager = walletManager,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.SpendLimit.route) {
                    SpendLimitScreen(
                        walletManager = walletManager,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.TransactionHistory.route) {
                    TransactionHistoryScreen(
                        onBack = { navController.popBackStack() }
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
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                composable(Screen.ScanQr.route) {
                    ScanQrScreen(
                        onPaymentScanned = { paymentRequest ->
                            pendingPaymentRequest = paymentRequest
                            navController.navigate(Screen.ConfirmPayment.route) {
                                popUpTo(Screen.ScanQr.route) { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() }
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
                                navController.navigate(Screen.Dashboard.route) {
                                    popUpTo(Screen.Dashboard.route) { inclusive = true }
                                }
                            },
                            onBack = {
                                pendingPaymentRequest = null
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}