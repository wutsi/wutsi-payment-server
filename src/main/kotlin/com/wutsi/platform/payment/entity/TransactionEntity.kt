package com.wutsi.platform.payment.entity

import java.time.OffsetDateTime
import javax.persistence.Entity
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
    val fromAccountId: Long = -1,
    val toAccountId: Long = -1,
    val description: String? = null,
    val amount: Double = 0.0,
    val currency: String = "",
    val created: OffsetDateTime = OffsetDateTime.now()
)
