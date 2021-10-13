package com.wutsi.platform.payment.dto

import org.springframework.format.`annotation`.DateTimeFormat
import java.time.LocalDate
import kotlin.Double
import kotlin.Long
import kotlin.String

public data class Balance(
    public val accountId: Long = 0,
    public val paymentMethodProvider: String = "",
    public val amount: Double = 0.0,
    public val amountForPayout: Double = 0.0,
    public val currency: String = "",
    @get:DateTimeFormat(pattern = "yyyy-MM-dd")
    public val synced: LocalDate = LocalDate.now()
)
