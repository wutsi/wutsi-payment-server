package com.wutsi.platform.payment.service

import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.stereotype.Service

@Service
class FeesCalculator {
    fun compute(type: TransactionType, amount: Double, tenant: Tenant): Double {
        val fee = tenant.fees.find { it.transactionType == type.name }
            ?: return 0.0

        return amount * fee.percent + fee.amount
    }
}
