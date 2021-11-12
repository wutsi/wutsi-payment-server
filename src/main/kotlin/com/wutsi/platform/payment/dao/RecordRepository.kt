package com.wutsi.platform.payment.dao

import com.wutsi.platform.payment.entity.RecordEntity
import com.wutsi.platform.payment.entity.TransactionEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface RecordRepository : CrudRepository<RecordEntity, Long> {
    fun findByTransaction(transaction: TransactionEntity): List<RecordEntity>
}
