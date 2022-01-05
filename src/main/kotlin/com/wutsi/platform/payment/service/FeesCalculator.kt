package com.wutsi.platform.payment.service

import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.AccountSummary
import com.wutsi.platform.account.dto.SearchAccountRequest
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.tenant.dto.Fee
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.stereotype.Service
import java.lang.Double.min

@Service
class FeesCalculator(
    private val accountApi: WutsiAccountApi
) {
    fun computeFees(tx: TransactionEntity, tenant: Tenant): Double {
        // Get the fee
        val accounts = accountApi.searchAccount(
            request = SearchAccountRequest(
                ids = listOfNotNull(tx.recipientId, tx.accountId)
            )
        ).accounts.map { it.id to it }.toMap()
        val fee = tenant.fees.find { canApply(tx, it, accounts) }
            ?: return 0.0

        // Compute the fees
        val baseAmount = tx.amount
        val feesAmount = min(baseAmount, fee.amount + baseAmount * fee.percent)
        tx.fees = feesAmount
        tx.net = baseAmount - feesAmount
        tx.feesToSender = fee.applyToSender
        return tx.fees
    }

    private fun canApply(tx: TransactionEntity, fee: Fee, accounts: Map<Long, AccountSummary?>): Boolean {
        if (tx.type.name.equals(fee.transactionType, true)) {
            val recipient = accounts[tx.recipientId]

            if (recipient?.retail == true)
                return fee.retail == true || fee.retail == null
            else if (recipient?.business == true)
                return fee.business == true || fee.business == null
            else
                return fee.retail == null && fee.business == null
        }
        return false
    }
}
