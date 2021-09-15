package com.wutsi.platform.payment.dto

import java.time.LocalDate
import javax.validation.constraints.Size

public data class Balance(
    public val accountId: Long = 0,
    public val amount: Double = 0.0,
    @get:Size(max = 3)
    public val currency: String = "",
    public val synced: LocalDate = LocalDate.now()
)
