package com.wutsi.platform.payment.dto

import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size
import kotlin.Double
import kotlin.Long
import kotlin.String

public data class CreateChargeRequest(
    public val merchantId: Long = 0,
    public val customerId: Long = 0,
    public val applicationId: Long = 0,
    @get:NotBlank
    public val paymentMethodToken: String = "",
    public val externalId: String = "",
    @get:NotBlank
    public val description: String = "",
    @get:Min(0)
    public val amount: Double = 0.0,
    @get:NotBlank
    @get:Size(max = 3)
    public val currency: String = ""
)
