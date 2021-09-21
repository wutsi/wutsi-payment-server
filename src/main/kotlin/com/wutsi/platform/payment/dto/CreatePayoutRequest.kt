package com.wutsi.platform.payment.dto

import kotlin.String

public data class CreatePayoutRequest(
    public val paymentMethodProvider: String = ""
)
