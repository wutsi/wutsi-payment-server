package com.wutsi.platform.payment.dao

import com.wutsi.platform.payment.entity.TransactionEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface TransactionRepository : CrudRepository<TransactionEntity, String> {
    fun findByReferenceId(referenceId: String): List<TransactionEntity>
    fun findByAccountId(toAccountId: Long): List<TransactionEntity>
    fun findByAccountIdAndCreatedGreaterThanEqual(accountId: Long, created: OffsetDateTime): List<TransactionEntity>
    fun findByAccountIdAndCreatedLessThan(accountId: Long, created: OffsetDateTime): List<TransactionEntity>
    fun findByAccountIdAndCreatedGreaterThanEqualAndCreatedLessThan(
        accountId: Long,
        createdMin: OffsetDateTime,
        createdMax: OffsetDateTime
    ): List<TransactionEntity>
}
