package com.wutsi.platform.payment.delegate

import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.Parameter
import com.wutsi.platform.core.error.ParameterType.PARAMETER_TYPE_PAYLOAD
import com.wutsi.platform.core.error.exception.BadRequestException
import com.wutsi.platform.core.logging.KVLogger
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.GatewayProvider
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.core.ErrorCode
import com.wutsi.platform.payment.core.Money
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.RecordRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.CreateCashoutRequest
import com.wutsi.platform.payment.dto.CreateCashoutResponse
import com.wutsi.platform.payment.entity.RecordEntity
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.entity.TransactionType.CASHOUT
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.event.TransactionEventPayload
import com.wutsi.platform.payment.exception.TransactionException
import com.wutsi.platform.payment.model.CreateTransferRequest
import com.wutsi.platform.payment.model.CreateTransferResponse
import com.wutsi.platform.payment.model.Party
import com.wutsi.platform.payment.service.AccountService
import com.wutsi.platform.payment.service.SecurityManager
import com.wutsi.platform.payment.service.TenantProvider
import com.wutsi.platform.payment.util.ErrorURN
import com.wutsi.platform.tenant.dto.Tenant
import feign.FeignException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
class CreateCashoutDelegate(
    private val securityManager: SecurityManager,
    private val accountApi: WutsiAccountApi,
    private val accountService: AccountService,
    private val transactionDao: TransactionRepository,
    private val recordDao: RecordRepository,
    private val tenantProvider: TenantProvider,
    private val gatewayProvider: GatewayProvider,
    private val logger: KVLogger,
    private val eventStream: EventStream,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(CreateCashoutDelegate::class.java)
    }

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
        logger.add("transaction_id", tx.id)

        // Perform the transfer
        try {
            ensureCanCashout(tx, tenant)

            val response = performTransaction(tx, paymentMethod)
            logger.add("gateway_status", response.status)
            logger.add("gateway_transaction_id", response.transactionId)
            logger.add("gateway_financial_transaction_id", response.financialTransactionId)

            if (response.status == Status.SUCCESSFUL) {
                onSuccess(tx, response, paymentMethod, tenant)
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
        if (request.currency != tenant.currency) {
            throw BadRequestException(
                error = Error(
                    code = ErrorURN.CURRENCY_NOT_SUPPORTED.urn,
                    parameter = Parameter(
                        type = PARAMETER_TYPE_PAYLOAD,
                        name = "currency",
                        value = request.currency
                    )
                )
            )
        }
    }

    private fun createTransaction(
        request: CreateCashoutRequest,
        paymentMethod: PaymentMethod,
        tenant: Tenant
    ): TransactionEntity =
        transactionDao.save(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                userId = securityManager.currentUserId(),
                tenantId = tenant.id,
                paymentMethodToken = request.paymentMethodToken,
                paymentMethodProvider = PaymentMethodProvider.valueOf(paymentMethod.provider),
                type = CASHOUT,
                amount = request.amount,
                currency = tenant.currency,
                status = Status.PENDING,
                created = OffsetDateTime.now(),
            )
        )

    private fun performTransaction(tx: TransactionEntity, paymentMethod: PaymentMethod): CreateTransferResponse {
        val paymentGateway = gatewayProvider.get(PaymentMethodProvider.valueOf(paymentMethod.provider))
        return paymentGateway.createTransfer(
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
        paymentMethod: PaymentMethod,
        tenant: Tenant
    ) {
        tx.status = Status.SUCCESSFUL
        tx.gatewayTransactionId = response.transactionId
        tx.financialTransactionId = response.financialTransactionId
        transactionDao.save(tx)

        updateLedger(tx, paymentMethod, tenant)
        publish(EventURN.TRANSACTION_SUCCESSFULL, tx)
    }

    private fun updateLedger(tx: TransactionEntity, paymentMethod: PaymentMethod, tenant: Tenant) {
        val userAccount = accountService.findUserAccount(tx.userId, tenant)

        // Create records
        val gatewayAccount = accountService.findGatewayAccount(paymentMethod, tenant)
        val records = listOf(
            RecordEntity.decrease(tx, userAccount, tx.amount),
            RecordEntity.decrease(tx, gatewayAccount, tx.amount),
        )
        recordDao.saveAll(records)

        // Update balance
        accountService.updateBalance(userAccount, -tx.amount)
        accountService.updateBalance(gatewayAccount, -tx.amount)
    }

    private fun ensureCanCashout(tx: TransactionEntity, tenant: Tenant) {
        // Check balance
        val userId = securityManager.currentUserId()
        val account = accountService.findUserAccount(userId, tenant)
        if (account.balance < tx.amount) {
            throw PaymentException(
                error = com.wutsi.platform.payment.core.Error(
                    code = ErrorCode.NOT_ENOUGH_FUNDS
                )
            )
        }

        // Make sure the recipient can cash-out
        try {
            val userId = securityManager.currentUserId()
            val user = accountApi.getAccount(userId).account
            if (!user.status.equals("ACTIVE", ignoreCase = true)) {
                throw PaymentException(
                    error = com.wutsi.platform.payment.core.Error(
                        code = ErrorCode.PAYMENT_NOT_APPROVED,
                        transactionId = tx.id!!
                    )
                )
            }
        } catch (ex: FeignException.NotFound) {
            throw PaymentException(
                error = com.wutsi.platform.payment.core.Error(
                    code = ErrorCode.PAYMENT_NOT_APPROVED,
                    transactionId = tx.id!!
                ),
                ex
            )
        }
    }

    private fun publish(type: EventURN, tx: TransactionEntity) {
        try {
            eventStream.publish(
                type.urn,
                TransactionEventPayload(
                    tenantId = tx.tenantId,
                    transactionId = tx.id!!,
                    type = TransactionType.CASHOUT.name,
                    amount = tx.amount,
                    currency = tx.currency
                )
            )
        } catch (ex: Exception) {
            LOGGER.error("Unable to publish event $type", ex)
        }
    }
}
