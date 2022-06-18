package com.wutsi.platform.payment.dto

import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size
import kotlin.Double
import kotlin.String

public data class CreateCashoutRequest(
    @get:NotBlank
    public val paymentMethodToken: String = "",
    @get:Min(0)
    public val amount: Double = 0.0,
    @get:NotBlank
    @get:Size(max = 3)
    public val currency: String = "",
    @get:NotBlank
    public val idempotencyKey: String = "",
)
