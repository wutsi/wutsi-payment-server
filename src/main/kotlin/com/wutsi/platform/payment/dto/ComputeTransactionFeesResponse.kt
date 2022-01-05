package com.wutsi.platform.payment.dto

import kotlin.Boolean
import kotlin.Double

public data class ComputeTransactionFeesResponse(
    public val fees: Double = 0.0,
    public val applyToSender: Boolean = false
)
