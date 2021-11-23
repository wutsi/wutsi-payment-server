package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.exception.ConflictException
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.core.Status.SUCCESSFUL
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.CreateTransferRequest
import com.wutsi.platform.payment.dto.CreateTransferResponse
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType.TRANSFER
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.event.EventURN.TRANSACTION_FAILED
import com.wutsi.platform.payment.event.EventURN.TRANSACTION_SUCCESSFULL
import com.wutsi.platform.payment.event.TransactionEventPayload
import com.wutsi.platform.payment.exception.TransactionException
import com.wutsi.platform.payment.service.TenantProvider
import com.wutsi.platform.payment.util.ErrorURN
import com.wutsi.platform.tenant.dto.Tenant
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
public class CreateTransferDelegate(
    private val transactionDao: TransactionRepository,
    private val tenantProvider: TenantProvider,
) : AbstractDelegate() {
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
        try {
            onSuccess(request, tx, tenant)
            return CreateTransferResponse(
                id = tx.id!!,
                status = Status.SUCCESSFUL.name
            )
        } catch (ex: PaymentException) {
            log(ex)

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

    private fun onSuccess(request: CreateTransferRequest, tx: TransactionEntity, tenant: Tenant) {
        updateBalance(securityManager.currentUserId(), -request.amount, tenant)
        updateBalance(request.recipientId, request.amount, tenant)

        publish(TRANSACTION_SUCCESSFULL, request, tx)
    }

    private fun onFailure(request: CreateTransferRequest, tx: TransactionEntity, ex: PaymentException) {
        tx.status = Status.FAILED
        tx.errorCode = ex.error.code.name
        transactionDao.save(tx)
        publish(TRANSACTION_FAILED, request, tx)
    }

    private fun createTransaction(request: CreateTransferRequest, tenant: Tenant): TransactionEntity {
        val tx = transactionDao.save(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                userId = securityManager.currentUserId(),
                tenantId = tenant.id,
                type = TRANSFER,
                amount = request.amount,
                fees = 0.0,
                net = request.amount,
                currency = tenant.currency,
                status = SUCCESSFUL,
                created = OffsetDateTime.now(),
                description = request.description
            )
        )

        logger.add("transaction_id", tx.id)
        return tx
    }

    private fun validateRequest(request: CreateTransferRequest, tenant: Tenant) {
        validateCurrency(request.currency, tenant)
        ensureBalanceAbove(securityManager.currentUserId(), request.amount, tenant)
        ensureCurrentUserActive()

        val recipient = accountApi.getAccount(request.recipientId).account
        if (!recipient.status.equals("ACTIVE", ignoreCase = true)) {
            throw ConflictException(
                error = Error(
                    code = ErrorURN.RECIPIENT_NOT_ACTIVE.urn,
                    data = mapOf("userId" to request.recipientId)
                ),
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
                    userId = securityManager.currentUserId(),
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
