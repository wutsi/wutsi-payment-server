package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.Parameter
import com.wutsi.platform.core.error.ParameterType.PARAMETER_TYPE_PAYLOAD
import com.wutsi.platform.core.error.exception.BadRequestException
import com.wutsi.platform.core.logging.KVLogger
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.core.ErrorCode
import com.wutsi.platform.payment.core.ErrorCode.NOT_ENOUGH_FUNDS
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.core.Status.SUCCESSFUL
import com.wutsi.platform.payment.dao.RecordRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.CreateTransferRequest
import com.wutsi.platform.payment.dto.CreateTransferResponse
import com.wutsi.platform.payment.entity.RecordEntity
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType.TRANSFER
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.event.EventURN.TRANSACTION_FAILED
import com.wutsi.platform.payment.event.EventURN.TRANSACTION_SUCCESSFULL
import com.wutsi.platform.payment.event.TransactionEventPayload
import com.wutsi.platform.payment.exception.TransactionException
import com.wutsi.platform.payment.service.AccountService
import com.wutsi.platform.payment.service.SecurityManager
import com.wutsi.platform.payment.service.TenantProvider
import com.wutsi.platform.payment.util.ErrorURN
import com.wutsi.platform.payment.util.ErrorURN.CURRENCY_NOT_SUPPORTED
import com.wutsi.platform.tenant.dto.Tenant
import feign.FeignException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
public class CreateTransferDelegate(
    private val securityManager: SecurityManager,
    private val accountService: AccountService,
    private val transactionDao: TransactionRepository,
    private val recordDao: RecordRepository,
    private val tenantProvider: TenantProvider,
    private val userApi: WutsiAccountApi,
    private val logger: KVLogger,
    private val eventStream: EventStream
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(CreateTransferDelegate::class.java)
    }

    @Transactional(noRollbackFor = [TransactionException::class])
    public fun invoke(request: CreateTransferRequest): CreateTransferResponse {
        logger.add("currency", request.currency)
        logger.add("amount", request.amount)
        logger.add("recipient_id", request.recipientId)
        logger.add("description", request.description)

        val tenant = tenantProvider.get()
        validateRequest(request, tenant)

        val tx = createTransaction(request, tenant)
        logger.add("transaction_id", tx.id)
        try {
            ensureCanTransfer(request, tx)

            onSuccess(request, tx)
            return CreateTransferResponse(
                id = tx.id!!,
                status = Status.SUCCESSFUL.name
            )
        } catch (ex: PaymentException) {
            logger.add("gateway_error_code", ex.error.code)
            logger.add("gateway_supplier_error_code", ex.error.supplierErrorCode)

            onFailure(request, tx, ex)
            throw TransactionException(
                error = Error(
                    code = ErrorURN.TRANSACTION_FAILED.urn,
                    downstreamCode = tx.errorCode,
                    data = mapOf("id" to tx.id!!)
                )
            )
        }
    }

    private fun onSuccess(request: CreateTransferRequest, tx: TransactionEntity) {
        updateLedger(request, tx)
        publish(TRANSACTION_SUCCESSFULL, request, tx)
    }

    private fun onFailure(request: CreateTransferRequest, tx: TransactionEntity, ex: PaymentException) {
        tx.status = Status.FAILED
        tx.errorCode = ex.error.code.name
        transactionDao.save(tx)
        publish(TRANSACTION_FAILED, request, tx)
    }

    private fun createTransaction(request: CreateTransferRequest, tenant: Tenant): TransactionEntity {
        val userId = securityManager.currentUserId()
        val fromAccount = accountService.findUserAccount(userId, tenant)
        val toAccount = accountService.findUserAccount(request.recipientId, tenant)

        return transactionDao.save(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                tenantId = tenant.id,
                type = TRANSFER,
                amount = request.amount,
                currency = tenant.currency,
                status = SUCCESSFUL,
                created = OffsetDateTime.now(),
                fromAccount = fromAccount,
                toAccount = toAccount,
                description = request.description
            )
        )
    }

    private fun updateLedger(request: CreateTransferRequest, tx: TransactionEntity) {
        // Update balance
        accountService.updateBalance(tx.fromAccount!!, -tx.amount)
        accountService.updateBalance(tx.toAccount!!, tx.amount)

        // Update the records
        val records = listOf(
            RecordEntity.decrease(tx, tx.fromAccount, tx.amount),
            RecordEntity.increase(tx, tx.toAccount, tx.amount),
        )
        recordDao.saveAll(records)
    }

    private fun validateRequest(request: CreateTransferRequest, tenant: Tenant) {
        if (request.currency != tenant.currency) {
            throw BadRequestException(
                error = Error(
                    code = CURRENCY_NOT_SUPPORTED.urn,
                    parameter = Parameter(
                        type = PARAMETER_TYPE_PAYLOAD,
                        name = "currency",
                        value = request.currency
                    )
                )
            )
        }
    }

    private fun ensureCanTransfer(request: CreateTransferRequest, tx: TransactionEntity) {
        // Check balance
        if (tx.fromAccount!!.balance < tx.amount) {
            throw PaymentException(
                error = com.wutsi.platform.payment.core.Error(
                    code = NOT_ENOUGH_FUNDS
                )
            )
        }

        // Make sure the recipient can accept the transfer
        try {
            val user = userApi.getAccount(request.recipientId).account
            if (!user.status.equals("ACTIVE", ignoreCase = true)) {
                throw PaymentException(
                    error = com.wutsi.platform.payment.core.Error(
                        code = ErrorCode.PAYEE_NOT_ALLOWED_TO_RECEIVE,
                        transactionId = tx.id!!
                    )
                )
            }
        } catch (ex: FeignException.NotFound) {
            throw PaymentException(
                error = com.wutsi.platform.payment.core.Error(
                    code = ErrorCode.PAYEE_NOT_ALLOWED_TO_RECEIVE,
                    transactionId = tx.id!!
                ),
                ex
            )
        }
    }

    private fun publish(type: EventURN, request: CreateTransferRequest, tx: TransactionEntity) {
        try {
            eventStream.publish(
                type.urn,
                TransactionEventPayload(
                    tenantId = tx.tenantId,
                    transactionId = tx.id!!,
                    type = TRANSFER.name,
                    senderId = securityManager.currentUserId(),
                    recipientId = request.recipientId,
                    amount = tx.amount,
                    currency = tx.currency
                )
            )
        } catch (ex: Exception) {
            LOGGER.error("Unable to publish event $type", ex)
        }
    }
}
