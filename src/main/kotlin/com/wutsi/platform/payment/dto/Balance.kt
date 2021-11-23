package com.wutsi.platform.payment.dto

import org.springframework.format.annotation.DateTimeFormat
import java.time.OffsetDateTime

public data class Balance(
    public val id: Long = -1,
    public val userId: Long = -1,
    public val amount: Double = 0.0,
    public val currency: String = "",
    @get:DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
    public val created: OffsetDateTime = OffsetDateTime.now(),
    @get:DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
    public val updated: OffsetDateTime = OffsetDateTime.now()
)
