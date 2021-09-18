package com.wutsi.platform.payment.dao

import com.wutsi.platform.payment.entity.PayoutEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface PayoutRepository : CrudRepository<PayoutEntity, String>
