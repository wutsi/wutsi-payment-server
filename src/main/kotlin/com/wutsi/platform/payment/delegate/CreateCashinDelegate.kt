package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.logging.KVLogger
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.GatewayProvider
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.core.Money
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.BalanceRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.CreateCashinRequest
import com.wutsi.platform.payment.dto.CreateCashinResponse
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType.CASHIN
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.exception.TransactionException
import com.wutsi.platform.payment.model.CreatePaymentRequest
import com.wutsi.platform.payment.model.CreatePaymentResponse
import com.wutsi.platform.payment.model.Party
import com.wutsi.platform.payment.service.SecurityManager
import com.wutsi.platform.payment.service.TenantProvider
import com.wutsi.platform.payment.util.ErrorURN
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
class CreateCashinDelegate(
    accountApi: WutsiAccountApi,
    eventStream: EventStream,
    balanceDao: BalanceRepository,

    private val securityManager: SecurityManager,
    private val transactionDao: TransactionRepository,
    private val tenantProvider: TenantProvider,
    private val gatewayProvider: GatewayProvider,
    private val logger: KVLogger,
) : AbstractDelegate(accountApi, balanceDao, eventStream) {
    @Transactional(noRollbackFor = [TransactionException::class])
    fun invoke(request: CreateCashinRequest): CreateCashinResponse {
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
            val response = cashin(tx, paymentMethod)

            if (response.status == Status.SUCCESSFUL) {
                onSuccess(tx, response, tenant)
            } else {
                onPending(tx, response)
            }

            return CreateCashinResponse(
                id = tx.id!!,
                status = tx.status.name
            )
        } catch (ex: PaymentException) {
            logger.add("gateway_error_code", ex.error.code)
            logger.add("gateway_supplier_error_code", ex.error.supplierErrorCode)

            onError(tx, ex)
            throw TransactionException(
                error = Error(
                    code = ErrorURN.TRANSACTION_FAILED.urn,
                    downstreamCode = ex.error.code.name,
                    data = mapOf(
                        "id" to tx.id!!
                    )
                )
            )
        }
    }

    private fun validateRequest(request: CreateCashinRequest, tenant: Tenant) {
        validateCurrency(request.currency, tenant)
    }

    private fun createTransaction(
        request: CreateCashinRequest,
        paymentMethod: PaymentMethod,
        tenant: Tenant
    ): TransactionEntity {
        val tx = transactionDao.save(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                userId = securityManager.currentUserId(),
                tenantId = tenant.id,
                paymentMethodToken = request.paymentMethodToken,
                paymentMethodProvider = PaymentMethodProvider.valueOf(paymentMethod.provider),
                type = CASHIN,
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

    private fun cashin(tx: TransactionEntity, paymentMethod: PaymentMethod): CreatePaymentResponse {
        val paymentGateway = gatewayProvider.get(PaymentMethodProvider.valueOf(paymentMethod.provider))
        val response = paymentGateway.createPayment(
            CreatePaymentRequest(
                payer = Party(
                    fullName = paymentMethod.ownerName,
                    phoneNumber = paymentMethod.phone!!.number
                ),
                amount = Money(tx.amount, tx.currency),
                externalId = tx.id!!,
                description = "Cash-in",
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

    private fun onPending(tx: TransactionEntity, response: CreatePaymentResponse) {
        tx.status = Status.PENDING
        tx.gatewayTransactionId = response.transactionId
        transactionDao.save(tx)
    }

    private fun onSuccess(
        tx: TransactionEntity,
        response: CreatePaymentResponse,
        tenant: Tenant
    ) {
        // Update transaction
        tx.status = Status.SUCCESSFUL
        tx.gatewayTransactionId = response.transactionId
        tx.financialTransactionId = response.financialTransactionId
        transactionDao.save(tx)

        // Update balance
        val balance = updateBalance(tx.userId, tx.net, tenant)
        logger.add("balance", balance.amount)

        publish(EventURN.TRANSACTION_SUCCESSFULL, tx)
    }
}
