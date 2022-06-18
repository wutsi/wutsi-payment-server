package com.wutsi.platform.payment.dto

public data class ComputeFeesResponse(
    public val fee: TransactionFee = TransactionFee(),
)
