package com.voltic.app.chain.explorer

import android.util.Log
import com.voltic.app.BuildConfig
import com.voltic.app.chain.ArbitrumClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigDecimal

class ExplorerClient {

    private val client = OkHttpClient()
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    companion object {
        private const val BASE_URL = "https://api.etherscan.io/v2/api"
        private const val PRICE_URL = "https://api.binance.com/api/v3/ticker/price?symbol=ETHUSDT"
        private const val TAG = "ExplorerClient"
    }

    /**
     * Fetches normal transaction list for a given address on Arbitrum Sepolia.
     * Supports pagination via the [page] parameter.
     */
    suspend fun getNormalTransactions(address: String, page: Int = 1): List<TransactionRecord> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.ARBISCAN_API_KEY
        
        // Etherscan v2 API parameters
        val url = "$BASE_URL?chainid=${ArbitrumClient.ARBITRUM_CHAIN_ID}" +
                "&module=account" +
                "&action=txlist" +
                "&address=$address" +
                "&startblock=0" +
                "&endblock=99999999" +
                "&page=$page" +
                "&offset=50" +
                "&sort=desc" +
                "&apikey=$apiKey"

        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Unexpected code $response")
                
                val body = response.body?.string() ?: return@withContext emptyList()
                val explorerResponse = json.decodeFromString<ExplorerResponse>(body)
                
                if (explorerResponse.status == "1") {
                    explorerResponse.result
                } else {
                    Log.w(TAG, "API returned status ${explorerResponse.status}: ${explorerResponse.message}")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching transactions", e)
            emptyList()
        }
    }

    /**
     * Fetches live ETH/USDT price from Binance.
     */
    suspend fun getEthPrice(): BigDecimal? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(PRICE_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val priceData = json.decodeFromString<EthPrice>(body)
                priceData.price.toBigDecimal()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching ETH price", e)
            null
        }
    }

    /**
     * Returns the Arbiscan URL for a given transaction hash.
     */
    fun getArbiscanUrl(txHash: String): String {
        return "https://sepolia.arbiscan.io/tx/$txHash"
    }
}
