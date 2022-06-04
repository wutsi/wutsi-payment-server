package com.wutsi.platform.payment.dto

import org.springframework.format.`annotation`.DateTimeFormat
import java.time.OffsetDateTime
import javax.validation.constraints.Min
import javax.validation.constraints.Size
import kotlin.Double
import kotlin.Long
import kotlin.String

public data class PaymentRequest(
    public val id: String = "",
    public val accountId: Long = 0,
    @get:Min(0)
    public val amount: Double = 0.0,
    public val currency: String = "",
    @get:Size(max = 100)
    public val description: String? = null,
    @get:Size(max = 36)
    public val orderId: String? = null,
    @get:DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
    public val created: OffsetDateTime = OffsetDateTime.now()
)
