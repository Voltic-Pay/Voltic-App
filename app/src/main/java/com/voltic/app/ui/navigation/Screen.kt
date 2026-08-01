package com.voltic.app.ui.navigation

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome_screen")
    object CreateWallet : Screen("create_wallet_screen")
    object RestoreWallet : Screen("restore_wallet_screen")
    object Dashboard : Screen("dashboard_screen")
    object GenerateQr : Screen("generate_qr_screen")
    object ScanQr : Screen("scan_qr_screen")
    object ConfirmPayment : Screen("confirm_payment_screen")
    object TransactionHistory : Screen("transaction_history_screen")
    object BackupSeed : Screen("backup_seed_screen")
    object SpendLimit : Screen("spend_limit_screen")
}
