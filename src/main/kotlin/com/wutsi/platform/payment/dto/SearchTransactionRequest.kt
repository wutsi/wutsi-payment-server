package com.wutsi.platform.payment.dto

import kotlin.Int
import kotlin.Long

public data class SearchTransactionRequest(
    public val userId: Long = 0,
    public val tenantId: Long = 0,
    public val limit: Int = 30,
    public val offset: Int = 0
)
