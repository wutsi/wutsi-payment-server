package com.wutsi.platform.payment.dto

import kotlin.Double
import kotlin.Long
import kotlin.String

public data class TransactionSummary(
    public val id: String = "",
    public val userId: Long = 0,
    public val recipientId: Long? = null,
    public val tenantId: Long = 0,
    public val type: String = "",
    public val paymentMethodProvider: String? = null,
    public val description: String? = null,
    public val amount: Double = 0.0,
    public val fees: Double = 0.0,
    public val net: Double = 0.0,
    public val currency: String = "",
    public val status: String = ""
)
