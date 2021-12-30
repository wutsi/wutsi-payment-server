package com.wutsi.platform.payment.entity

import java.time.OffsetDateTime
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "T_PAYMENT_REQUEST")
public data class PaymentRequestEntity(
    @Id
    val id: String? = null,
    val tenantId: Long = -1,
    val accountId: Long = -1,
    val amount: Double = 0.0,
    val currency: String = "",
    val description: String? = null,
    val invoiceId: String? = null,
    val expires: OffsetDateTime? = null,
    val created: OffsetDateTime = OffsetDateTime.now(),
)
