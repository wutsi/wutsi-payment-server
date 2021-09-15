package com.wutsi.platform.payment.entity

import java.time.LocalDate
import java.time.OffsetDateTime
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "T_BALANCE")
data class BalanceEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val accountId: Long = -1,
    var synced: LocalDate = LocalDate.now(),
    var amount: Double = 0.0,
    val currency: String = "",
    val created: OffsetDateTime = OffsetDateTime.now()
)
