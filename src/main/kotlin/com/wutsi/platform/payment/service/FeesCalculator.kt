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

        tx.fees = response.fees
        tx.net = request.amount - response.fees
        tx.feesToSender = response.applyToSender
        return tx.fees
    }

    fun computeFees(
        request: ComputeTransactionFeesRequest,
        tenant: Tenant,
        accounts: Map<Long, AccountSummary?>
    ): ComputeTransactionFeesResponse {
        // Get the fee
        val fee = tenant.fees.find { canApply(request, it, accounts) }
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
        if (request.transactionType.equals(fee.transactionType, true)) {
            val recipient = accounts[request.recipientId]
            return recipient?.business == fee.business
        }
        return false
    }
}
