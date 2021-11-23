package com.wutsi.platform.payment.dao

import com.wutsi.platform.payment.entity.GatewayEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Deprecated("")
@Repository
interface GatewayRepository : CrudRepository<GatewayEntity, Long> {
    fun findByCodeIgnoreCase(code: String): Optional<GatewayEntity>
}
