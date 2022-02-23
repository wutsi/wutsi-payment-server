package com.wutsi.platform.payment.event

data class TransactionEventPayload(
    val transactionId: String = "",
    val orderId: String? = null,
    val type: String = "",
)
