package com.voltic.app.ui.screens.main

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.voltic.app.R
import com.voltic.app.chain.explorer.ExplorerClient
import com.voltic.app.chain.explorer.TransactionRecord
import com.voltic.app.ui.components.TransactionItem
import com.voltic.app.ui.components.TransactionSkeletonItem
import com.voltic.app.wallet.WalletManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val walletManager = remember { WalletManager(context) }
    val explorer = remember { ExplorerClient() }
    val scope = rememberCoroutineScope()

    var transactions by remember { mutableStateOf<List<TransactionRecord>>(emptyList()) }
    var address by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(value = true) }
    var isLoadingMore by remember { mutableStateOf(value = false) }
    var currentPage by remember { mutableIntStateOf(value = 1) }
    var hasReachedEnd by remember { mutableStateOf(value = false) }

    fun loadTransactions(page: Int) {
        scope.launch {
            if (page == 1) isLoading = true else isLoadingMore = true

            address?.let { addr ->
                val newTxs = explorer.getNormalTransactions(addr, page)
                if (newTxs.isEmpty()) {
                    hasReachedEnd = true
                } else {
                    transactions = if (page == 1) newTxs else transactions + newTxs
                    if (newTxs.size < 50) hasReachedEnd = true
                }
            }

            isLoading = false
            isLoadingMore = false
        }
    }

    LaunchedEffect(Unit) {
        val credentials = walletManager.loadExistingWalletAsync()
        address = credentials?.address
        loadTransactions(1)
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("History", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(id = R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
                items(10) {
                    TransactionSkeletonItem()
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        } else if (transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No transactions found", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(transactions) { tx ->
                    TransactionItem(
                        transaction = tx,
                        currentAddress = address ?: "",
                        onClick = {
                            val url = explorer.getArbiscanUrl(tx.hash)
                            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                            context.startActivity(intent)
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }

                if (!hasReachedEnd) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoadingMore) {
                                CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
                            } else {
                                FilledTonalButton(
                                    onClick = {
                                        currentPage++
                                        loadTransactions(currentPage)
                                    },
                                    modifier = Modifier.height(56.dp),
                                    shape = CircleShape
                                ) {
                                    Text("Load More", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}