package com.wutsi.platform.payment.dto

import kotlin.collections.List

public data class SearchTransactionResponse(
    public val transactions: List<TransactionSummary> = emptyList()
)
