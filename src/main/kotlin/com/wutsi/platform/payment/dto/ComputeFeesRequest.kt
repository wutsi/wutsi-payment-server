package com.wutsi.platform.payment.dto

import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import kotlin.Double
import kotlin.String

public data class ComputeFeesRequest(
    @get:NotBlank
    public val transactionType: String = "",
    public val paymentMethodType: String? = null,
    @get:Min(0)
    public val amount: Double = 0.0,
    @get:NotBlank
    public val currency: String = ""
)
