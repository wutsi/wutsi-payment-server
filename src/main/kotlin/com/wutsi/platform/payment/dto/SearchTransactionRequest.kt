package com.wutsi.platform.payment.dto

import kotlin.Int
import kotlin.Long

public data class SearchTransactionRequest(
    public val accountId: Long = 0,
    public val limit: Int = 30,
    public val offset: Int = 0
)
