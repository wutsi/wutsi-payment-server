package com.wutsi.platform.payment.dto

import org.springframework.format.`annotation`.DateTimeFormat
import java.time.OffsetDateTime
import javax.validation.constraints.Size
import kotlin.Double
import kotlin.Long
import kotlin.String

public data class Payout(
    public val id: String = "",
    public val userId: Long? = null,
    public val accountId: Long = 0,
    public val description: String = "",
    public val gatewayTransactionId: String = "",
    public val financialTransactionId: String? = null,
    public val amount: Double = 0.0,
    @get:Size(max = 3)
    public val currency: String = "",
    public val status: String = "",
    public val errorCode: String? = null,
    public val supplierErrorCode: String? = null,
    public val paymentMethodProvider: String = "",
    @get:DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
    public val created: OffsetDateTime = OffsetDateTime.now(),
    @get:DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
    public val updated: OffsetDateTime = OffsetDateTime.now()
)
