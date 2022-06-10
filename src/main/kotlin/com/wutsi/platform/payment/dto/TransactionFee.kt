package com.wutsi.platform.payment.dto

import javax.validation.constraints.NotBlank
import kotlin.Boolean
import kotlin.Double
import kotlin.String

public data class TransactionFee(
    public val amount: Double = 0.0,
    public val fees: Double = 0.0,
    public val applyFeesToSender: Boolean = false,
    public val senderAmount: Double = 0.0,
    public val recipientAmount: Double = 0.0,
    @get:NotBlank
    public val currency: String = ""
)
