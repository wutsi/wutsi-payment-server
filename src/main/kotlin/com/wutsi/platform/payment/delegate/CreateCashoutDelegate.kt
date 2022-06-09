package com.wutsi.platform.payment.delegate

import com.wutsi.platform.account.dto.Account
import com.wutsi.platform.account.dto.AccountSummary
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.account.dto.SearchAccountRequest
import com.wutsi.platform.payment.GatewayProvider
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.core.Money
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dto.CreateCashoutRequest
import com.wutsi.platform.payment.dto.CreateCashoutResponse
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType.CASHOUT
import com.wutsi.platform.payment.error.ErrorURN
import com.wutsi.platform.payment.error.TransactionException
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.model.CreateTransferRequest
import com.wutsi.platform.payment.model.CreateTransferResponse
import com.wutsi.platform.payment.model.Party
import com.wutsi.platform.payment.service.TenantProvider
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
class CreateCashoutDelegate(
    private val tenantProvider: TenantProvider,
    private val gatewayProvider: GatewayProvider,
) : AbstractDelegate() {
    @Transactional(noRollbackFor = [TransactionException::class])
    fun invoke(request: CreateCashoutRequest): CreateCashoutResponse {
        logger.add("currency", request.currency)
        logger.add("amount", request.amount)
        logger.add("payment_token", request.paymentMethodToken)

        // Tenant
        val tenant = tenantProvider.get()
        val accounts = accountApi.searchAccount(
            request = SearchAccountRequest(
                ids = listOfNotNull(securityManager.currentUserId())
            )
        ).accounts.map { it.id to it }.toMap()
        validateRequest(request, tenant, accounts)

        // Gateway
        val userId = securityManager.currentUserId()!!
        val paymentMethod = accountApi.getPaymentMethod(
            id = userId,
            token = request.paymentMethodToken
        ).paymentMethod

        // Create transaction
        val payee = accountApi.getAccount(userId).account
        val tx = createTransaction(request, paymentMethod, tenant, payee)
        try {
            // Update balance
            validateTransaction(tx)
            updateBalance(tx.accountId, -tx.amount, tenant)

            val response = cashout(tx, paymentMethod, payee)
            log(response)

            if (response.status == Status.SUCCESSFUL) {
                onSuccess(tx, response)
            } else {
                onPending(tx, response.transactionId)
            }

            return CreateCashoutResponse(
                id = tx.id!!,
                status = tx.status.name
            )
        } catch (ex: PaymentException) {
            logger.add("gateway_error_code", ex.error.code)
            logger.add("gateway_supplier_error_code", ex.error.supplierErrorCode)

            onError(tx, ex, tenant)
            throw createTransactionException(tx, ErrorURN.TRANSACTION_FAILED, ex)
        } finally {
            log(tx)
        }
    }

    private fun validateRequest(request: CreateCashoutRequest, tenant: Tenant, accounts: Map<Long, AccountSummary>) {
        ensureCurrentUserActive(accounts)
        validateCurrency(request.currency, tenant)
    }

    private fun validateTransaction(tx: TransactionEntity) {
        ensureBalanceAbove(securityManager.currentUserId()!!, tx)
    }

    private fun createTransaction(
        request: CreateCashoutRequest,
        paymentMethod: PaymentMethod,
        tenant: Tenant,
        payee: Account
    ): TransactionEntity {
        val tx = transactionDao.save(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                accountId = payee.id,
                tenantId = tenant.id,
                paymentMethodToken = request.paymentMethodToken,
                paymentMethodProvider = PaymentMethodProvider.valueOf(paymentMethod.provider),
                type = CASHOUT,
                amount = request.amount,
                fees = 0.0,
                net = request.amount,
                currency = tenant.currency,
                created = OffsetDateTime.now(),
            )
        )
        return tx
    }

    private fun cashout(tx: TransactionEntity, paymentMethod: PaymentMethod, payee: Account): CreateTransferResponse {
        val gateway = gatewayProvider.get(PaymentMethodProvider.valueOf(paymentMethod.provider))
        logger.add("gateway", gateway::class.java.simpleName)

        return gateway.createTransfer(
            CreateTransferRequest(
                payee = Party(
                    fullName = paymentMethod.ownerName,
                    phoneNumber = paymentMethod.phone!!.number,
                    email = payee.email
                ),
                amount = Money(tx.amount, tx.currency),
                externalId = tx.id!!,
                description = "Cash-out",
                payerMessage = null
            )
        )
    }

    fun onError(tx: TransactionEntity, ex: PaymentException, tenant: Tenant) {
        if (tx.status == Status.FAILED)
            return

        // Revert balance
        updateBalance(tx.accountId, tx.amount, tenant)

        // Update the transaction
        super.onError(tx, ex)
    }

    fun onSuccess(
        tx: TransactionEntity,
        response: CreateTransferResponse
    ) {
        if (tx.status == Status.SUCCESSFUL)
            return

        // Update transaction
        tx.status = Status.SUCCESSFUL
        tx.gatewayTransactionId = response.transactionId
        tx.financialTransactionId = response.financialTransactionId
        tx.gatewayFees = response.fees.value
        transactionDao.save(tx)

        publish(EventURN.TRANSACTION_SUCCESSFUL, tx)
    }
}
