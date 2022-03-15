package com.wutsi.platform.payment.service

import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.AccountSummary
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.payment.GatewayProvider
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodType
import com.wutsi.platform.payment.core.Money
import com.wutsi.platform.payment.dto.ComputeTransactionFeesRequest
import com.wutsi.platform.payment.dto.ComputeTransactionFeesResponse
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.model.GetFeesRequest
import com.wutsi.platform.tenant.dto.Fee
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.stereotype.Service
import java.lang.Double.max
import java.lang.Double.min

@Service
class FeesCalculator(
    private val gatewayProvider: GatewayProvider,
    private val accountApi: WutsiAccountApi,
    private val securityManager: SecurityManager,
) {
    fun computeFees(
        tx: TransactionEntity,
        tenant: Tenant,
        accounts: Map<Long, AccountSummary?>,
        paymentMethod: PaymentMethod?
    ) {
        val request = ComputeTransactionFeesRequest(
            transactionType = tx.type.name,
            senderId = tx.accountId,
            recipientId = tx.recipientId,
            amount = tx.amount,
            paymentMethodToken = paymentMethod?.token
        )
        val response = computeFees(request, tenant, accounts)

        val base = tx.amount
        tx.feesToSender = response.applyToSender
        tx.fees = response.fees
        tx.gatewayFees = response.gatewayFees
        if (response.applyToSender) {
            tx.net = base
            tx.amount = base + tx.fees + tx.gatewayFees
        } else {
            tx.amount = base
            tx.net = max(0.0, base - tx.fees - tx.gatewayFees)
        }
    }

    fun computeFees(
        request: ComputeTransactionFeesRequest,
        tenant: Tenant,
        accounts: Map<Long, AccountSummary?>
    ): ComputeTransactionFeesResponse {
        val paymentMethod = request.paymentMethodToken?.let {
            accountApi.getPaymentMethod(
                id = securityManager.currentUserId(),
                token = it
            ).paymentMethod
        }
        return computeFees(request, tenant, accounts, paymentMethod)
    }

    private fun computeFees(
        request: ComputeTransactionFeesRequest,
        tenant: Tenant,
        accounts: Map<Long, AccountSummary?>,
        paymentMethod: PaymentMethod?
    ): ComputeTransactionFeesResponse {
        // Get the fee
        val fees = tenant.fees.filter { canApply(request, it, accounts) }
            .sortedByDescending { score(it) }
        val fee = fees.firstOrNull()
            ?: Fee()

        // Compute the fees
        val baseAmount = request.amount
        return ComputeTransactionFeesResponse(
            fees = min(baseAmount, fee.amount + baseAmount * fee.percent),
            gatewayFees = computeGatewayFees(request, paymentMethod),
            applyToSender = fee.applyToSender
        )
    }

    private fun computeGatewayFees(
        request: ComputeTransactionFeesRequest,
        paymentMethod: PaymentMethod?
    ): Double {
        if (paymentMethod == null)
            return 0.0

        val gateway = gatewayProvider.get(PaymentMethodProvider.valueOf(paymentMethod.provider.uppercase()))
        return gateway.getFees(
            GetFeesRequest(
                amount = Money(request.amount, request.currency),
                paymentMethodType = PaymentMethodType.valueOf(paymentMethod.type)
            )
        ).fees.value
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
        ((fee.toRetail == null && fee.toBusiness == null) || fee.toRetail == recipient?.retail) ||
            (fee.toBusiness == true && recipient?.business == true)

    private fun score(fee: Fee): Int {
        var value = 0

        if (fee.fromRetail != null)
            value += 10

        if (fee.toRetail != null)
            value += 10

        if (fee.toBusiness != null)
            value += 5

        return value
    }
}
