package com.voltic.app.chain.explorer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExplorerResponse(
    val status: String,
    val message: String,
    val result: List<TransactionRecord>
)

@Serializable
data class TransactionRecord(
    val blockNumber: String,
    val timeStamp: String,
    val hash: String,
    val from: String,
    val to: String,
    val value: String,
    val contractAddress: String? = null,
    val input: String? = null,
    val type: String? = null,
    val gas: String,
    val gasUsed: String,
    val isError: String,
    val errCode: String? = null,
    @SerialName("txreceipt_status")
    val txReceiptStatus: String? = null // For normal transactions
)

@Serializable
data class EthPrice(
    val symbol: String,
    val price: String
)
