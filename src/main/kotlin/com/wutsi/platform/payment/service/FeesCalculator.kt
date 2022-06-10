package com.wutsi.platform.payment.service

import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.stereotype.Service
import java.lang.Double.max

@Service
class FeesCalculator {
    fun apply(
        tx: TransactionEntity,
        paymentMethodType: String?,
        tenant: Tenant
    ) {
        val obj = findFees(tx.type, paymentMethodType, tenant)
            ?: findFees(tx.type, null, tenant)
            ?: return

        val amount = tx.amount
        val fees = amount * obj.percent + obj.amount

        tx.amount = if (obj.applyToSender) tx.amount + fees else tx.amount
        tx.fees = fees
        tx.net = max(0.0, tx.amount - tx.fees)
        tx.applyFeesToSender = obj.applyToSender
    }

    private fun findFees(type: TransactionType, paymentMethodType: String?, tenant: Tenant) =
        tenant.fees.find { it.transactionType == type.name && it.paymentMethodType == paymentMethodType }
}
