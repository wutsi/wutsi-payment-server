package com.wutsi.platform.payment.entity

import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodProvider.UNKNOWN
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
    val paymentMethodProvider: PaymentMethodProvider = UNKNOWN,
    var synced: LocalDate = LocalDate.now(),
    var amount: Double = 0.0,
    val currency: String = "",
    val created: OffsetDateTime = OffsetDateTime.now()
)
