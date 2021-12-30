package com.wutsi.platform.payment.dao

import com.wutsi.platform.payment.entity.PaymentRequestEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentRequestRepository : CrudRepository<PaymentRequestEntity, String>
