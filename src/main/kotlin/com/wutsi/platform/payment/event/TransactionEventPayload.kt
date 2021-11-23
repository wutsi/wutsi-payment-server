package com.wutsi.platform.payment.event

data class TransactionEventPayload(
    val tenantId: Long = -1,
    val transactionId: String = "",
    val type: String = "",
    val userId: Long = -1,
    val recipientId: Long? = null,
    val amount: Double = 0.0,
    val currency: String = "",
)
