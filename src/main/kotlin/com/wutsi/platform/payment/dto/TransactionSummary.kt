package com.wutsi.platform.payment.dto

import org.springframework.format.annotation.DateTimeFormat
import java.time.OffsetDateTime

public data class TransactionSummary(
    public val id: String = "",
    public val userId: Long = 0,
    public val recipientId: Long? = null,
    public val tenantId: Long = 0,
    public val type: String = "",
    public val paymentMethodProvider: String? = null,
    public val description: String? = null,
    public val amount: Double = 0.0,
    public val fees: Double = 0.0,
    public val net: Double = 0.0,
    public val currency: String = "",
    public val status: String = "",
    @get:DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
    public val created: OffsetDateTime = OffsetDateTime.now()
)