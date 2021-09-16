package com.wutsi.platform.payment.dao

import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.entity.BalanceEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface BalanceRepository : CrudRepository<BalanceEntity, Long> {
    @Deprecated("Should return list")
    fun findByAccountId(merchantId: Long): Optional<BalanceEntity>
    fun findByAccountIdAndPaymentMethodProvider(merchantId: Long, paymentMethodProvider: PaymentMethodProvider): Optional<BalanceEntity>
}
