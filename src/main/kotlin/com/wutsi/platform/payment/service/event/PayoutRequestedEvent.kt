package com.wutsi.platform.payment.service.event

data class PayoutRequestedEvent(
    val accountId: Long = -1
)
