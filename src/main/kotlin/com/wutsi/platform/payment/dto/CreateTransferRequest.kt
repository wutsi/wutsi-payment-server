package com.wutsi.platform.payment.dto

import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

public data class CreateTransferRequest(
    public val recipientId: Long = 0,
    @get:Min(0)
    public val amount: Double = 0.0,
    @get:NotBlank
    @get:Size(max = 3)
    public val currency: String = "",
    public val description: String? = null
)
