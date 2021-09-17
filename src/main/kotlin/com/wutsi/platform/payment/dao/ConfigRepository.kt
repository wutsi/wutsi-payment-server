package com.wutsi.platform.payment.dao

import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.entity.ConfigEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ConfigRepository : CrudRepository<ConfigEntity, Long> {
    fun findByPaymentMethodProviderAndCountry(paymentMethodProvider: PaymentMethodProvider, country: String): Optional<ConfigEntity>
}
