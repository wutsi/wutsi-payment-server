package com.wutsi.platform.payment.event

data class TransactionEventPayload(
    val transactionId: String = "",
    val type: String = "",
    val senderId: Long = -1,
    val recipientId: Long = -1
)
