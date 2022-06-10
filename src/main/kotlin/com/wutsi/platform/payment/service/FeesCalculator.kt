package com.wutsi.platform.payment.service

import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.stereotype.Service
import java.lang.Double.max

@Service
class FeesCalculator {
    fun apply(
        tx: TransactionEntity,
        paymentMethod: PaymentMethod?,
        tenant: Tenant
    ) {
        val fees = findFees(tx.type, paymentMethod?.type, tenant)
            ?: findFees(tx.type, null, tenant)
            ?: return

        val amount = tx.amount
        tx.fees = amount * fees.percent + fees.amount
        tx.net = max(0.0, amount - tx.fees)
        tx.applyFeesToSender = fees.applyToSender
    }

    private fun findFees(type: TransactionType, paymentMethodType: String?, tenant: Tenant) =
        tenant.fees.find { it.transactionType == type.name && it.paymentMethodType == paymentMethodType }

    @Deprecated("")
    fun compute(
        type: TransactionType,
        amount: Double,
        tenant: Tenant
    ): Double {
        val fee = tenant.fees.find { it.transactionType == type.name }
            ?: return 0.0

        return amount * fee.percent + fee.amount
    }
}
