package com.wutsi.platform.payment.dto

import org.springframework.format.`annotation`.DateTimeFormat
import java.time.OffsetDateTime
import kotlin.Double
import kotlin.Long
import kotlin.String

public data class Transaction(
    public val id: String = "",
    public val accountId: Long = 0,
    public val recipientId: Long? = null,
    public val type: String = "",
    public val paymentMethodToken: String? = null,
    public val paymentMethodProvider: String? = null,
    public val description: String? = null,
    public val amount: Double = 0.0,
    public val fees: Double = 0.0,
    public val net: Double = 0.0,
    public val currency: String = "",
    public val status: String = "",
    public val gatewayTransactionId: String? = null,
    public val financialTransactionId: String? = null,
    public val errorCode: String? = null,
    public val supplierErrorCode: String? = null,
    @get:DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
    public val created: OffsetDateTime = OffsetDateTime.now()
)
