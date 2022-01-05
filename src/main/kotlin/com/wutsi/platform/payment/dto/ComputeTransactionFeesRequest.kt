package com.wutsi.platform.payment.dto

import javax.validation.constraints.Min
import kotlin.Double
import kotlin.Long
import kotlin.String

public data class ComputeTransactionFeesRequest(
    public val transactionType: String = "",
    public val recipientId: Long? = null,
    @get:Min(0)
    public val amount: Double = 0.0
)
