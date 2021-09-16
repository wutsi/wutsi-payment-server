package com.wutsi.platform.payment.entity

import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodProvider.UNKNOWN
import java.time.OffsetDateTime
import javax.persistence.Entity
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "T_TRANSACTION")
data class TransactionEntity(
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    val id: Long = -1,
    val referenceId: String = "",
    val type: TransactionType = TransactionType.UNKNOWN,
    val accountId: Long = -1,
    val description: String? = null,
    val amount: Double = 0.0,
    val currency: String = "",
    val created: OffsetDateTime = OffsetDateTime.now(),

    @Enumerated
    val paymentMethodProvider: PaymentMethodProvider = UNKNOWN
)
