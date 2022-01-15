package com.wutsi.platform.payment.service

import com.wutsi.platform.account.dto.AccountSummary
import com.wutsi.platform.payment.dto.ComputeTransactionFeesRequest
import com.wutsi.platform.payment.dto.ComputeTransactionFeesResponse
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.tenant.dto.Fee
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.stereotype.Service
import java.lang.Double.min

@Service
class FeesCalculator {
    fun computeFees(tx: TransactionEntity, tenant: Tenant, accounts: Map<Long, AccountSummary?>): Double {
        val request = ComputeTransactionFeesRequest(
            transactionType = tx.type.name,
            recipientId = tx.recipientId,
            amount = tx.amount
        )
        val response = computeFees(request, tenant, accounts)

        val base = tx.amount
        tx.feesToSender = response.applyToSender
        if (response.applyToSender) {
            tx.amount = base + response.fees
            tx.fees = response.fees
            tx.net = base
        } else {
            tx.amount = base
            tx.fees = response.fees
            tx.net = base - response.fees
        }
        return tx.fees
    }

    fun computeFees(
        request: ComputeTransactionFeesRequest,
        tenant: Tenant,
        accounts: Map<Long, AccountSummary?>
    ): ComputeTransactionFeesResponse {
        // Get the fee
        val fee = tenant.fees.filter { canApply(request, it, accounts) }
            .sortedByDescending { score(it) }
            .firstOrNull()
            ?: return ComputeTransactionFeesResponse()

        // Compute the fees
        val baseAmount = request.amount
        return ComputeTransactionFeesResponse(
            fees = min(baseAmount, fee.amount + baseAmount * fee.percent),
            applyToSender = fee.applyToSender
        )
    }

    private fun canApply(
        request: ComputeTransactionFeesRequest,
        fee: Fee,
        accounts: Map<Long, AccountSummary?>
    ): Boolean {
        if (request.transactionType.equals(fee.transactionType, true) && request.amount >= fee.threshold) {
            val recipient = accounts[request.recipientId]

            return if (recipient?.business == true) {
                (fee.business == true || fee.business == null) && (recipient.retail == fee.retail || fee.retail == null)
            } else {
                fee.business == false || fee.business == null
            }
        }
        return false
    }

    private fun score(fee: Fee): Int {
        var value = 0
        if (fee.business != null)
            value += 10
        if (fee.business == true && fee.retail != null)
            value += 1

        return value
    }
}
