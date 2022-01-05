package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.account.dto.AccountSummary
import com.wutsi.platform.account.dto.SearchAccountRequest
import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.exception.ForbiddenException
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.core.Status.SUCCESSFUL
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.CreateTransferRequest
import com.wutsi.platform.payment.dto.CreateTransferResponse
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType.TRANSFER
import com.wutsi.platform.payment.error.ErrorURN
import com.wutsi.platform.payment.error.TransactionException
import com.wutsi.platform.payment.event.EventURN.TRANSACTION_FAILED
import com.wutsi.platform.payment.event.EventURN.TRANSACTION_SUCCESSFULL
import com.wutsi.platform.payment.service.FeesCalculator
import com.wutsi.platform.payment.service.TenantProvider
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
public class CreateTransferDelegate(
    private val transactionDao: TransactionRepository,
    private val tenantProvider: TenantProvider,
    private val feesCalculator: FeesCalculator,
) : AbstractDelegate() {
    @Transactional(noRollbackFor = [TransactionException::class])
    public fun invoke(request: CreateTransferRequest): CreateTransferResponse {
        logger.add("currency", request.currency)
        logger.add("amount", request.amount)
        logger.add("recipient_id", request.recipientId)
        logger.add("description", request.description)

        val tenant = tenantProvider.get()
        validateRequest(request, tenant)

        val accounts = accountApi.searchAccount(
            request = SearchAccountRequest(
                ids = listOfNotNull(request.recipientId, securityManager.currentUserId())
            )
        ).accounts.map { it.id to it }.toMap()

        val tx = createTransaction(request, tenant, accounts)
        try {
            validateTransaction(tx)

            onSuccess(request, tx, tenant)
            return CreateTransferResponse(
                id = tx.id!!,
                status = Status.SUCCESSFUL.name
            )
        } catch (ex: PaymentException) {
            log(ex)

            onFailure(tx, ex)
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
        updateBalance(securityManager.currentUserId(), -tx.amount, tenant)
        updateBalance(request.recipientId, tx.net, tenant)

        publish(TRANSACTION_SUCCESSFULL, tx)
    }

    private fun onFailure(tx: TransactionEntity, ex: PaymentException) {
        tx.status = Status.FAILED
        tx.errorCode = ex.error.code.name
        transactionDao.save(tx)
        publish(TRANSACTION_FAILED, tx)
    }

    private fun createTransaction(
        request: CreateTransferRequest,
        tenant: Tenant,
        accounts: Map<Long, AccountSummary?>
    ): TransactionEntity {
        val recipient = accounts[request.recipientId]
        val tx = transactionDao.save(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                accountId = securityManager.currentUserId(),
                recipientId = request.recipientId,
                tenantId = tenant.id,
                type = TRANSFER,
                amount = request.amount,
                fees = 0.0,
                net = request.amount,
                currency = tenant.currency,
                status = SUCCESSFUL,
                created = OffsetDateTime.now(),
                description = request.description,
                business = recipient?.business ?: false,
                retail = recipient?.retail ?: false
            )
        )
        feesCalculator.computeFees(tx, tenant, accounts)
        logger.add("transaction_id", tx.id)
        return tx
    }

    private fun validateRequest(request: CreateTransferRequest, tenant: Tenant) {
        if (request.recipientId == securityManager.currentUserId())
            throw ForbiddenException(
                error = Error(
                    code = ErrorURN.SELF_TRANSACTION_ERROR.urn
                )
            )

        validateCurrency(request.currency, tenant)
        ensureCurrentUserActive()
        ensureRecipientActive(request.recipientId)
    }

    private fun validateTransaction(tx: TransactionEntity) {
        ensureBalanceAbove(securityManager.currentUserId(), tx)
    }
}
