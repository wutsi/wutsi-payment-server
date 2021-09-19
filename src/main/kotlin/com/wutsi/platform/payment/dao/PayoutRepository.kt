package com.wutsi.platform.payment.dao

import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.entity.PayoutEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface PayoutRepository : CrudRepository<PayoutEntity, String> {
    fun findByStatus(status: Status): List<PayoutEntity>
}
