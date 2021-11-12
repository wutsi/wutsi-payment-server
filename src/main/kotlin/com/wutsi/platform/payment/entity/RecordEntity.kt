package com.wutsi.platform.payment.entity

import java.time.OffsetDateTime
import javax.persistence.Entity
import javax.persistence.FetchType.LAZY
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(name = "T_RECORD")
data class RecordEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "transaction_fk")
    val transaction: TransactionEntity = TransactionEntity(),

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "account_fk")
    val account: AccountEntity = AccountEntity(),

    val debit: Double = 0.0,
    val credit: Double = 0.0,
    val currency: String = "",
    val created: OffsetDateTime = OffsetDateTime.now(),
) {
    companion object {
        fun increase(transaction: TransactionEntity, account: AccountEntity, amount: Double): RecordEntity =
            RecordEntity(
                transaction = transaction,
                account = account,
                currency = transaction.currency,
                debit = if (account.type.increaseOnDebit) amount else 0.0,
                credit = if (!account.type.increaseOnDebit) amount else 0.0
            )
    }
}
