package com.wutsi.platform.payment.dao

import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.entity.ChargeEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ChargeRepository : CrudRepository<ChargeEntity, String> {
    fun findByStatus(status: Status): List<ChargeEntity>
}
