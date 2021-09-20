package com.wutsi.platform.payment.dto

import kotlin.Long
import kotlin.String

public data class CreatePayoutRequest(
    public val accountId: Long = 0,
    public val paymentMethodProvider: String = ""
)
