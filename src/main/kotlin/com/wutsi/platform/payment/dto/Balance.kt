package com.wutsi.platform.payment.dto

import java.time.LocalDate

public data class Balance(
    public val accountId: Long = 0,
    public val amount: Double = 0.0,
    public val paymentMethodProvider: String = "",
    public val currency: String = "",
    public val synced: LocalDate = LocalDate.now()
)
