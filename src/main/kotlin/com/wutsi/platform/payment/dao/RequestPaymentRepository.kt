package com.wutsi.platform.payment.dao

import com.wutsi.platform.payment.entity.RequestPaymentEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface RequestPaymentRepository : CrudRepository<RequestPaymentEntity, String>
