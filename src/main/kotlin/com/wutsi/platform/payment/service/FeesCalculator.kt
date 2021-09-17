package com.wutsi.platform.payment.service

import com.wutsi.platform.payment.entity.ChargeEntity
import org.springframework.stereotype.Service
import java.lang.Double.min

@Service
class FeesCalculator(
    private val configService: ConfigService,
    private val accountService: AccountService
) {
    fun compute(charge: ChargeEntity): Double {
        val paymentMethod = accountService.findPaymentMethod(charge.customerId, charge.paymentMethodToken)
        val config = configService.findConfig(charge.paymentMethodProvider, paymentMethod.phone?.country)
        val fees = charge.amount * config.feesPercent + config.feesValue
        return min(fees, charge.amount)
    }
}
