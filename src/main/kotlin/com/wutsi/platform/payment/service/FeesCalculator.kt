package com.wutsi.platform.payment.service

import com.wutsi.platform.payment.entity.ChargeEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class FeesCalculator(
    @Value("\${wutsi.application.fees.rate}") val rate: Double
) {
    fun compute(charge: ChargeEntity): Double =
        rate * charge.amount
}
