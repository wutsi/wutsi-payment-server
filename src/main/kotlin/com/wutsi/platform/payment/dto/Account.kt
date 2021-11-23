package com.wutsi.platform.payment.dto

import org.springframework.format.annotation.DateTimeFormat
import java.time.OffsetDateTime

@Deprecated("")
public data class Account(
    public val id: Long = 0,
    public val type: String = "",
    public val name: String? = null,
    public val balance: Double = 0.0,
    public val currency: String = "",
    @get:DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
    public val created: OffsetDateTime = OffsetDateTime.now()
)
