package com.wutsi.platform.payment.dao

import com.wutsi.platform.payment.entity.BalanceEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface BalanceRepository : CrudRepository<BalanceEntity, Long> {
    fun findByAccountId(accountId: Long): Optional<BalanceEntity>
}
