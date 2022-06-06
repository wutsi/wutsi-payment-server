package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.account.dto.AccountSummary
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.account.dto.SearchAccountRequest
import com.wutsi.platform.payment.GatewayProvider
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.core.Money
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dto.CreateCashinRequest
import com.wutsi.platform.payment.dto.CreateCashinResponse
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType.CASHIN
import com.wutsi.platform.payment.error.ErrorURN
import com.wutsi.platform.payment.error.TransactionException
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.model.CreatePaymentRequest
import com.wutsi.platform.payment.model.CreatePaymentResponse
import com.wutsi.platform.payment.model.Party
import com.wutsi.platform.payment.service.TenantProvider
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
class CreateCashinDelegate(
    private val tenantProvider: TenantProvider,
    private val gatewayProvider: GatewayProvider,
) : AbstractDelegate() {
    @Transactional(noRollbackFor = [TransactionException::class])
    fun invoke(request: CreateCashinRequest): CreateCashinResponse {
        logger.add("currency", request.currency)
        logger.add("amount", request.amount)
        logger.add("payment_token", "******")

        // Tenant
        val tenant = tenantProvider.get()
        val accounts = accountApi.searchAccount(
            request = SearchAccountRequest(
                ids = listOfNotNull(securityManager.currentUserId())
            )
        ).accounts.map { it.id to it }.toMap()
        validateRequest(request, tenant, accounts)

        // Gateway
        val paymentMethod = accountApi.getPaymentMethod(
            id = securityManager.currentUserId(),
            token = request.paymentMethodToken
        ).paymentMethod

        // Create transaction
        val tx = createTransaction(request, paymentMethod, tenant)

        // Perform the transfer
        try {
            val response = cashin(tx, paymentMethod)
            logger.add("gateway_status", response.status)
            logger.add("gateway_transaction_id", response.transactionId)

            if (response.status == Status.SUCCESSFUL) {
                onSuccess(tx, response, tenant)
            } else {
                onPending(tx, response.transactionId)
            }

            return CreateCashinResponse(
                id = tx.id!!,
                status = tx.status.name
            )
        } catch (ex: PaymentException) {
            log(ex)
            onError(tx, ex)
            throw createTransactionException(tx, ErrorURN.TRANSACTION_FAILED, ex)
        } finally {
            log(tx)
        }
    }

    private fun validateRequest(request: CreateCashinRequest, tenant: Tenant, accounts: Map<Long, AccountSummary>) {
        ensureCurrentUserActive(accounts)
        validateCurrency(request.currency, tenant)
    }

    private fun createTransaction(
        request: CreateCashinRequest,
        paymentMethod: PaymentMethod,
        tenant: Tenant,
    ): TransactionEntity {
        val tx = transactionDao.save(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                accountId = securityManager.currentUserId(),
                tenantId = tenant.id,
                paymentMethodToken = request.paymentMethodToken,
                paymentMethodProvider = PaymentMethodProvider.valueOf(paymentMethod.provider),
                type = CASHIN,
                amount = request.amount,
                fees = 0.0,
                net = request.amount,
                currency = tenant.currency,
                created = OffsetDateTime.now(),
            )
        )
        return tx
    }

    private fun cashin(tx: TransactionEntity, paymentMethod: PaymentMethod): CreatePaymentResponse {
        val gateway = gatewayProvider.get(PaymentMethodProvider.valueOf(paymentMethod.provider))
        logger.add("gateway", gateway::class.java.simpleName)

        val response = gateway.createPayment(
            CreatePaymentRequest(
                payer = Party(
                    fullName = paymentMethod.ownerName,
                    phoneNumber = paymentMethod.phone!!.number
                ),
                amount = Money(tx.amount, tx.currency),
                externalId = tx.id!!,
                description = "",
                payerMessage = null
            )
        )
        return response
    }

    @Transactional
    fun onSuccess(
        tx: TransactionEntity,
        response: CreatePaymentResponse,
        tenant: Tenant
    ) {
        if (tx.status == Status.SUCCESSFUL)
            return

        // Update balance
        updateBalance(tx.accountId, tx.net, tenant)

        // Update transaction
        tx.status = Status.SUCCESSFUL
        tx.gatewayTransactionId = response.transactionId
        tx.financialTransactionId = response.financialTransactionId
        tx.gatewayFees = response.fees.value
        transactionDao.save(tx)

        publish(EventURN.TRANSACTION_SUCCESSFUL, tx)
    }
}
