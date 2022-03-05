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
            senderId = tx.accountId,
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
        val fees = tenant.fees.filter { canApply(request, it, accounts) }
            .sortedByDescending { score(it) }
        val fee = fees.firstOrNull()
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
        if (!request.transactionType.equals(fee.transactionType, true))
            return false

        if (request.amount < fee.threshold)
            return false

        if (!canApplyToSender(fee, request.senderId?.let { accounts[it] } ?: null))
            return false

        if (!canApplyToRecipient(fee, request.recipientId?.let { accounts[it] } ?: null))
            return false

        return true
    }

    private fun canApplyToSender(fee: Fee, sender: AccountSummary?): Boolean =
        fee.fromRetail == null || fee.fromRetail == sender?.retail

    private fun canApplyToRecipient(fee: Fee, recipient: AccountSummary?): Boolean =
        ((fee.toRetail == null && fee.toBusinees == null) || fee.toRetail == recipient?.retail) ||
        (fee.toBusinees == true && recipient?.business == true)

    private fun score(fee: Fee): Int {
        var value = 0

        if (fee.fromRetail != null)
            value += 10

        if (fee.toRetail != null)
            value += 10

        if (fee.toBusinees != null)
            value += 5

        return value
    }
}
