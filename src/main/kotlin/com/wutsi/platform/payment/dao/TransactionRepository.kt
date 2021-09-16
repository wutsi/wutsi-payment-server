package com.wutsi.platform.payment.dao

import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.entity.TransactionEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface TransactionRepository : CrudRepository<TransactionEntity, String> {
    fun findByReferenceId(referenceId: String): List<TransactionEntity>

    @Deprecated("")
    fun findByAccountId(toAccountId: Long): List<TransactionEntity>

    fun findByAccountIdAndPaymentMethodProvider(
        accountId: Long,
        paymentMethodProvider: PaymentMethodProvider
    ): List<TransactionEntity>

    fun findByAccountIdAndPaymentMethodProviderAndCreatedGreaterThanEqual(
        accountId: Long,
        paymentMethodProvider: PaymentMethodProvider,
        created: OffsetDateTime
    ): List<TransactionEntity>

    fun findByAccountIdAndPaymentMethodProviderAndCreatedLessThan(
        accountId: Long,
        paymentMethodProvider: PaymentMethodProvider,
        created: OffsetDateTime
    ): List<TransactionEntity>

    fun findByAccountIdAndPaymentMethodProviderAndCreatedGreaterThanEqualAndCreatedLessThan(
        accountId: Long,
        paymentMethodProvider: PaymentMethodProvider,
        createdMin: OffsetDateTime,
        createdMax: OffsetDateTime
    ): List<TransactionEntity>
}
