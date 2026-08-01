package com.voltic.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.voltic.app.settings.SpendLimitPreferences
import com.voltic.app.transport.nfc.NfcReaderManager
import com.voltic.app.ui.VolticApp
import com.voltic.app.wallet.WalletManager

class MainActivity : FragmentActivity() {

    private var deepLinkUriState by mutableStateOf<String?>(null)
    private var nfcReaderManager: NfcReaderManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        SpendLimitPreferences.init(this)

        val walletManager = WalletManager(applicationContext)
        deepLinkUriState = intent?.data?.toString()

        setContent {
            VolticApp(
                walletManager = walletManager,
                deepLinkUri = deepLinkUriState
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkUriState = intent.data?.toString()
    }

    override fun onPause() {
        super.onPause()
        nfcReaderManager?.stop()
        nfcReaderManager = null
    }
}