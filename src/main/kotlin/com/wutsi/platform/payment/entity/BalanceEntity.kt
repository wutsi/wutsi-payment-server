package com.wutsi.platform.payment.entity

import java.time.OffsetDateTime
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "T_BALANCE")
data class BalanceEntity(
    @Id
    val id: Long? = null,
    val userId: Long = -1,
    val tenantId: Long = -1,
    val amount: Double = 0.0,
    val currency: Double = 0.0,
    val created: OffsetDateTime = OffsetDateTime.now(),
    val updated: OffsetDateTime = OffsetDateTime.now(),
)
