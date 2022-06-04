package com.wutsi.platform.payment.dto

public data class SearchTransactionRequest(
    public val accountId: Long? = null,
    public val type: String? = null,
    public val status: List<String> = emptyList(),
    public val orderId: String? = null,
    public val limit: Int = 30,
    public val offset: Int = 0
)
