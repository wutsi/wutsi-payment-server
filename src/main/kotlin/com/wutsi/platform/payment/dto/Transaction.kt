package com.wutsi.platform.payment.dto

import org.springframework.format.`annotation`.DateTimeFormat
import java.time.OffsetDateTime
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import kotlin.Double
import kotlin.Long
import kotlin.String

public data class Transaction(
    public val id: String = "",
    public val accountId: Long = 0,
    @get:NotNull
    public val recipientId: Long? = null,
    public val type: String = "",
    @get:NotBlank
    public val paymentMethodToken: String? = null,
    @get:NotBlank
    public val paymentMethodProvider: String? = null,
    @get:NotBlank
    public val description: String? = null,
    public val amount: Double = 0.0,
    public val fees: Double = 0.0,
    public val net: Double = 0.0,
    public val currency: String = "",
    public val status: String = "",
    @get:NotBlank
    public val gatewayTransactionId: String? = null,
    @get:NotBlank
    public val financialTransactionId: String? = null,
    @get:NotBlank
    public val errorCode: String? = null,
    @get:NotBlank
    public val supplierErrorCode: String? = null,
    @get:DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
    public val created: OffsetDateTime = OffsetDateTime.now()
)
