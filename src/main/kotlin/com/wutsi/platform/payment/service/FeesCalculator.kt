package com.wutsi.platform.payment.service

import com.wutsi.platform.core.logging.KVLogger
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.tenant.dto.Fee
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.stereotype.Service
import java.lang.Double.max
import kotlin.math.ceil

@Service
class FeesCalculator(
    private val logger: KVLogger
) {
    fun apply(
        tx: TransactionEntity,
        paymentMethodType: String?,
        tenant: Tenant
    ) {
        val obj = findFees(tx.type, paymentMethodType, tenant)
            ?: findFees(tx.type, null, tenant)
        log(obj)

        if (obj == null) {
            tx.fees = 0.0
            tx.net = max(0.0, tx.amount - tx.fees)
            tx.applyFeesToSender = false
        } else {
            val amount = tx.amount
            val fees = ceil(amount * obj.percent + obj.amount)

            tx.amount = if (obj.applyToSender) tx.amount + fees else tx.amount
            tx.fees = fees
            tx.net = max(0.0, tx.amount - tx.fees)
            tx.applyFeesToSender = obj.applyToSender
        }
    }

    private fun log(obj: Fee?) {
        logger.add("fees_amount", obj?.amount)
        logger.add("fees_percent", obj?.percent)
        logger.add("fees_apply_to_sender", obj?.applyToSender)
        logger.add("fees_payment_method_type", obj?.paymentMethodType)
        logger.add("fees_transaction_type", obj?.transactionType)
    }

    private fun findFees(type: TransactionType, paymentMethodType: String?, tenant: Tenant) =
        tenant.fees.find { it.transactionType == type.name && it.paymentMethodType == paymentMethodType }
}
