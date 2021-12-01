package com.wutsi.platform.payment.delegate

import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.core.error.Error
import com.wutsi.platform.payment.GatewayProvider
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.core.Money
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.TransactionRepository
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
    private val transactionDao: TransactionRepository,
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
        validateRequest(request, tenant)

        // Gateway
        val paymentMethod = accountApi.getPaymentMethod(
            id = securityManager.currentUserId(),
            token = request.paymentMethodToken
        ).paymentMethod

        // Create transaction
        val tx = createTransaction(request, paymentMethod, tenant)

        // Perform the transfer
        try {
            val response = cashout(tx, paymentMethod)

            if (response.status == Status.SUCCESSFUL) {
                onSuccess(tx, response, tenant)
            } else {
                onPending(tx, response)
            }

            return CreateCashoutResponse(
                id = tx.id!!,
                status = tx.status.name
            )
        } catch (paymentEx: PaymentException) {
            logger.add("gateway_error_code", paymentEx.error.code)
            logger.add("gateway_supplier_error_code", paymentEx.error.supplierErrorCode)

            onError(tx, paymentEx)
            throw TransactionException(
                error = Error(
                    code = ErrorURN.TRANSACTION_FAILED.urn,
                    downstreamCode = paymentEx.error.code.name,
                    data = mapOf(
                        "id" to tx.id!!
                    )
                )
            )
        }
    }

    private fun validateRequest(request: CreateCashoutRequest, tenant: Tenant) {
        ensureCurrentUserActive()
        ensureBalanceAbove(securityManager.currentUserId(), request.amount, tenant)
        validateCurrency(request.currency, tenant)
    }

    private fun createTransaction(
        request: CreateCashoutRequest,
        paymentMethod: PaymentMethod,
        tenant: Tenant
    ): TransactionEntity {
        val tx = transactionDao.save(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                accountId = securityManager.currentUserId(),
                tenantId = tenant.id,
                paymentMethodToken = request.paymentMethodToken,
                paymentMethodProvider = PaymentMethodProvider.valueOf(paymentMethod.provider),
                type = CASHOUT,
                amount = request.amount,
                fees = 0.0,
                net = request.amount,
                currency = tenant.currency,
                status = Status.PENDING,
                created = OffsetDateTime.now(),
            )
        )

        logger.add("transaction_id", tx.id)
        return tx
    }

    private fun cashout(tx: TransactionEntity, paymentMethod: PaymentMethod): CreateTransferResponse {
        val paymentGateway = gatewayProvider.get(PaymentMethodProvider.valueOf(paymentMethod.provider))
        val response = paymentGateway.createTransfer(
            CreateTransferRequest(
                payee = Party(
                    fullName = paymentMethod.ownerName,
                    phoneNumber = paymentMethod.phone!!.number
                ),
                amount = Money(tx.amount, tx.currency),
                externalId = tx.id!!,
                description = "Cash-out",
                payerMessage = null
            )
        )

        logger.add("gateway_status", response.status)
        logger.add("gateway_transaction_id", response.transactionId)
        logger.add("gateway_financial_transaction_id", response.financialTransactionId)
        return response
    }

    private fun onError(tx: TransactionEntity, ex: PaymentException) {
        tx.status = Status.FAILED
        tx.errorCode = ex.error.code.name
        tx.supplierErrorCode = ex.error.supplierErrorCode
        tx.gatewayTransactionId = ex.error.transactionId
        transactionDao.save(tx)

        publish(EventURN.TRANSACTION_FAILED, tx)
    }

    private fun onPending(tx: TransactionEntity, response: CreateTransferResponse) {
        tx.status = Status.PENDING
        tx.gatewayTransactionId = response.transactionId
        transactionDao.save(tx)
    }

    private fun onSuccess(
        tx: TransactionEntity,
        response: CreateTransferResponse,
        tenant: Tenant
    ) {
        // Update balance
        val balance = updateBalance(tx.accountId, -tx.net, tenant)
        logger.add("balance", balance.amount)

        // Update transaction
        tx.status = Status.SUCCESSFUL
        tx.gatewayTransactionId = response.transactionId
        tx.financialTransactionId = response.financialTransactionId
        transactionDao.save(tx)

        publish(EventURN.TRANSACTION_SUCCESSFULL, tx)
    }
}
