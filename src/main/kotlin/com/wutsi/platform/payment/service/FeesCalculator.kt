package com.wutsi.platform.payment.service

import com.wutsi.platform.payment.entity.ChargeEntity
import org.springframework.stereotype.Service

@Service
class FeesCalculator {
    fun compute(charge: ChargeEntity): Double =
        0.01 * charge.amount
}
