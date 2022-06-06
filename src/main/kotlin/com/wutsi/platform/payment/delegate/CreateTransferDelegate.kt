package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.account.dto.AccountSummary
import com.wutsi.platform.account.dto.SearchAccountRequest
import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.exception.ForbiddenException
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.core.Status.SUCCESSFUL
import com.wutsi.platform.payment.dto.CreateTransferRequest
import com.wutsi.platform.payment.dto.CreateTransferResponse
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType.TRANSFER
import com.wutsi.platform.payment.error.ErrorURN
import com.wutsi.platform.payment.error.TransactionException
import com.wutsi.platform.payment.event.EventURN.TRANSACTION_SUCCESSFUL
import com.wutsi.platform.payment.service.TenantProvider
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
class CreateTransferDelegate(
    private val tenantProvider: TenantProvider,
) : AbstractDelegate() {
    @Transactional(noRollbackFor = [TransactionException::class])
    fun invoke(request: CreateTransferRequest): CreateTransferResponse {
        logger.add("currency", request.currency)
        logger.add("amount", request.amount)
        logger.add("recipient_id", request.recipientId)
        logger.add("description", request.description)

        val tenant = tenantProvider.get()

        val accounts = accountApi.searchAccount(
            request = SearchAccountRequest(
                ids = listOfNotNull(request.recipientId, securityManager.currentUserId())
            )
        ).accounts.map { it.id to it }.toMap()

        validateRequest(request, tenant, accounts)

        val tx = createTransaction(request, tenant, accounts)
        try {
            validateTransaction(tx)
            if (tx.status == Status.PENDING) {
                onPending(tx, null)
            } else if (tx.status == Status.SUCCESSFUL) {
                onSuccess(tx, tenant)
            }
            return CreateTransferResponse(
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

    fun onSuccess(tx: TransactionEntity, tenant: Tenant) {
        updateBalance(tx.accountId, -tx.amount, tenant)
        updateBalance(tx.recipientId!!, tx.net, tenant)

        publish(TRANSACTION_SUCCESSFUL, tx)
    }

    private fun createTransaction(
        request: CreateTransferRequest,
        tenant: Tenant,
        accounts: Map<Long, AccountSummary?>
    ): TransactionEntity {
        val business = isBusinessTransaction(request, accounts)
        val now = OffsetDateTime.now()
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
                created = now,
                description = request.description,
                business = business,
            )
        )
        return tx
    }

    private fun isBusinessTransaction(request: CreateTransferRequest, accounts: Map<Long, AccountSummary?>): Boolean =
        accounts[request.recipientId]?.business == true || accounts[securityManager.currentUserId()]?.business == true

    private fun validateRequest(
        request: CreateTransferRequest,
        tenant: Tenant,
        accounts: Map<Long, AccountSummary>
    ) {
        val currentUserId = securityManager.currentUserId()
        if (request.recipientId == currentUserId)
            throw ForbiddenException(
                error = Error(
                    code = ErrorURN.SELF_TRANSACTION_ERROR.urn
                )
            )

        validateCurrency(request.currency, tenant)
        ensureCurrentUserActive(accounts)
        ensureRecipientValid(request.recipientId, accounts)
        ensureRecipientActive(request.recipientId, accounts)
    }

    private fun validateTransaction(tx: TransactionEntity) {
        ensureBalanceAbove(securityManager.currentUserId(), tx)
    }
}
