package com.wutsi.platform.payment.dto

import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size
import kotlin.Double
import kotlin.Long
import kotlin.String

public data class CreateChargeRequest(
    public val paymentMethodToken: String? = null,
    public val recipientId: Long = 0,
    @get:Min(0)
    public val amount: Double = 0.0,
    @get:NotBlank
    @get:Size(max = 3)
    public val currency: String = "",
    public val orderId: String? = null,
    @get:Size(max = 100)
    public val description: String? = null,
    @get:NotBlank
    public val idempotencyKey: String = ""
)
