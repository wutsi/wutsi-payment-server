package com.wutsi.platform.payment.event

data class TransactionEventPayload(
    val tenantId: Long = -1,
    val transactionId: String = "",
    val type: String = "",
    val senderId: Long = -1,
    val recipientId: Long = -1,
    val amount: Double = 0.0,
    val currency: String = "",
)
