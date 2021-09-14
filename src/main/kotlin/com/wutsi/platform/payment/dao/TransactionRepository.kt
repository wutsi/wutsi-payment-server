package com.wutsi.platform.payment.dao

import com.wutsi.platform.payment.entity.TransactionEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface TransactionRepository : CrudRepository<TransactionEntity, String> {
    fun findByReferenceId(referenceId: String): List<TransactionEntity>
}
