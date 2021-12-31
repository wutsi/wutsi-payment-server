package com.wutsi.platform.payment.dto

import kotlin.Int
import kotlin.Long
import kotlin.String

public data class SearchTransactionRequest(
    public val accountId: Long? = null,
    public val paymentRequestId: String? = null,
    public val status: String? = null,
    public val limit: Int = 30,
    public val offset: Int = 0
)
