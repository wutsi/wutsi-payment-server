package com.wutsi.platform.payment.dao

import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface TransactionRepository : CrudRepository<TransactionEntity, String> {
    @Query("SELECT T FROM TransactionEntity T WHERE T.tenantId = ?1 AND (T.accountId=?2 OR T.recipientId=?2)")
    fun findByTenantIdAndAccountId(tenantId: Long, accountId: Long, page: Pageable): List<TransactionEntity>

    fun findByTypeAndStatus(type: TransactionType, status: Status, page: Pageable): List<TransactionEntity>
}
