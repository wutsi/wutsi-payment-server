package com.wutsi.platform.payment.dto

import org.springframework.format.annotation.DateTimeFormat
import java.time.OffsetDateTime
import javax.validation.constraints.Size

public data class Charge(
    public val id: String = "",
    public val merchantId: Long = 0,
    public val customerId: Long = 0,
    public val applicationId: Long = 0,
    public val userId: Long = 0,
    public val paymentMethodToken: String = "",
    public val externalId: String = "",
    public val description: String = "",
    public val amount: Double = 0.0,
    public val gatewayTransactionId: String = "",
    public val paymentMethodType: String = "",
    public val paymentMethodProvider: String = "",
    @get:Size(max = 3)
    public val currency: String = "",
    public val status: String = "",
    public val errorCode: String? = null,
    public val supplierErrorCode: String? = null,
    @get:DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
    public val created: OffsetDateTime = OffsetDateTime.now()
)
