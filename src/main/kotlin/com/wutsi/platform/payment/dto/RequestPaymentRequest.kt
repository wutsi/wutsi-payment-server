package com.wutsi.platform.payment.dto

public data class RequestPaymentRequest(
    public val amount: Double = 0.0,
    public val currency: String = "",
    public val description: String? = null,
    public val invoiceId: String? = null,
    public val timeToLive: Int? = null
)
