package com.wutsi.platform.payment.dao

import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface TransactionRepository : CrudRepository<TransactionEntity, String> {
    fun findByStatus(status: Status, page: Pageable): List<TransactionEntity>
    fun findByTypeAndStatus(type: TransactionType, status: Status, page: Pageable): List<TransactionEntity>
    fun findByIdempotencyKey(idempotencyKey: String): Optional<TransactionEntity>
}
