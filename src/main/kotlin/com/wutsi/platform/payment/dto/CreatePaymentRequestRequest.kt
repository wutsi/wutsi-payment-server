package com.wutsi.platform.payment.dto

import javax.validation.constraints.Min
import javax.validation.constraints.Size
import kotlin.Double
import kotlin.Int
import kotlin.String

public data class CreatePaymentRequestRequest(
    @get:Min(0)
    public val amount: Double = 0.0,
    public val currency: String = "",
    @get:Size(max = 100)
    public val description: String? = null,
    @get:Size(max = 36)
    public val orderId: String? = null,
    public val timeToLive: Int? = null
)
