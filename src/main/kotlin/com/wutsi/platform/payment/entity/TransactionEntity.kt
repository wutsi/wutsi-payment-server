package com.wutsi.platform.payment.entity

import java.time.OffsetDateTime
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "T_TRANSACTION")
data class TransactionEntity(
    @Id
    val id: String = "",
    val type: TransactionType = TransactionType.UNKNOWN,
    val fromAccountId: Long = -1,
    val toAccountId: Long = -1,
    val description: String = "",
    val amount: Double = 0.0,
    val fees: Double = 0.0,
    val net: Double = 0.0,
    val currency: String = "",
    val created: OffsetDateTime = OffsetDateTime.now()
)
