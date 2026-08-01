package com.voltic.app.payload

interface PaymentRequest {
    val to: String
    val amountEth: String?
    val chainId: Long
}