package com.wutsi.platform.payment.dto

import org.springframework.format.`annotation`.DateTimeFormat
import java.time.OffsetDateTime
import javax.validation.constraints.Size
import kotlin.Double
import kotlin.Long
import kotlin.String

public data class Charge(
    public val id: String = "",
    public val userId: Long = 0,
    public val merchantId: Long = 0,
    public val customerId: Long = 0,
    public val applicationId: Long? = null,
    public val paymentMethodToken: String = "",
    public val externalId: String = "",
    public val description: String = "",
    public val amount: Double = 0.0,
    public val gatewayTransactionId: String = "",
    @get:Size(max = 3)
    public val currency: String = "",
    public val status: String = "",
    public val errorCode: String? = null,
    public val supplierErrorCode: String? = null,
    public val paymentMethodType: String = "",
    public val paymentMethodProvider: String = "",
    @get:DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
    public val created: OffsetDateTime = OffsetDateTime.now()
)
