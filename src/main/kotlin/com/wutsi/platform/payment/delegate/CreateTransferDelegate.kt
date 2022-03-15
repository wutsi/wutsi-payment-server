package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.account.dto.AccountSummary
import com.wutsi.platform.account.dto.SearchAccountRequest
import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.Parameter
import com.wutsi.platform.core.error.ParameterType
import com.wutsi.platform.core.error.exception.BadRequestException
import com.wutsi.platform.core.error.exception.ForbiddenException
import com.wutsi.platform.core.error.exception.NotFoundException
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.core.ErrorCode
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.core.Status.PENDING
import com.wutsi.platform.payment.core.Status.SUCCESSFUL
import com.wutsi.platform.payment.dao.PaymentRequestRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.CreateTransferRequest
import com.wutsi.platform.payment.dto.CreateTransferResponse
import com.wutsi.platform.payment.entity.PaymentRequestEntity
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType.TRANSFER
import com.wutsi.platform.payment.error.ErrorURN
import com.wutsi.platform.payment.error.TransactionException
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.event.EventURN.TRANSACTION_FAILED
import com.wutsi.platform.payment.event.EventURN.TRANSACTION_SUCCESSFUL
import com.wutsi.platform.payment.service.FeesCalculator
import com.wutsi.platform.payment.service.TenantProvider
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
public class CreateTransferDelegate(
    private val paymentRequestDao: PaymentRequestRepository,
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
        logger.add("order_id", request.orderId)
        logger.add("payment_request_id", request.paymentRequestId)

        val tenant = tenantProvider.get()

        val accounts = accountApi.searchAccount(
            request = SearchAccountRequest(
                ids = listOfNotNull(request.recipientId, securityManager.currentUserId())
            )
        ).accounts.map { it.id to it }.toMap()

        val payment = request.paymentRequestId?.let {
            paymentRequestDao.findById(it)
                .orElseThrow {
                    NotFoundException(
                        error = Error(
                            code = ErrorURN.PAYMENT_REQUEST_NOT_FOUND.urn,
                            parameter = Parameter(
                                name = "paymentRequestId",
                                value = it,
                                type = ParameterType.PARAMETER_TYPE_PAYLOAD
                            )
                        )
                    )
                }
        }

        validateRequest(request, payment, tenant, accounts)

        val tx = createTransaction(request, payment, tenant, accounts)
        try {
            validateTransaction(tx, payment)
            if (tx.status == Status.PENDING) {
                onPending(tx)
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
        }
    }

    private fun onPending(tx: TransactionEntity) {
        publish(EventURN.TRANSACTION_PENDING, tx)
    }

    public fun onSuccess(tx: TransactionEntity, tenant: Tenant) {
        updateBalance(tx.accountId, -tx.amount, tenant)
        updateBalance(tx.recipientId!!, tx.net, tenant)

        publish(TRANSACTION_SUCCESSFUL, tx)
    }

    @Transactional
    public fun onError(tx: TransactionEntity, ex: PaymentException) {
        tx.status = Status.FAILED
        tx.errorCode = ex.error.code.name
        transactionDao.save(tx)
        publish(TRANSACTION_FAILED, tx)
    }

    private fun createTransaction(
        request: CreateTransferRequest,
        payment: PaymentRequestEntity?,
        tenant: Tenant,
        accounts: Map<Long, AccountSummary?>
    ): TransactionEntity {
        val retail = isRetailTransaction(request, accounts)
        val business = isBusinessTransaction(request, accounts)
        val now = OffsetDateTime.now()
        val tx = transactionDao.save(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                accountId = securityManager.currentUserId(),
                recipientId = request.recipientId,
                tenantId = tenant.id,
                type = TRANSFER,
                amount = payment?.amount ?: request.amount,
                fees = 0.0,
                net = request.amount,
                currency = tenant.currency,
                status = if (retail) PENDING else SUCCESSFUL,
                created = now,
                expires = if (retail) now.plusMinutes(5) else null, // Expires in 1 minute
                description = request.description,
                business = business,
                retail = retail,
                requiresApproval = retail,
                approved = null,
                orderId = payment?.orderId ?: request.orderId,
                paymentRequestId = payment?.id,
            )
        )

        feesCalculator.computeFees(tx, tenant, accounts, null)
        logger.add("transaction_id", tx.id)
        logger.add("transaction_fees", tx.fees)
        logger.add("transaction_amount", tx.amount)
        logger.add("transaction_net", tx.net)
        return tx
    }

    private fun isRetailTransaction(request: CreateTransferRequest, accounts: Map<Long, AccountSummary?>): Boolean =
        accounts[request.recipientId]?.retail == true || accounts[securityManager.currentUserId()]?.retail == true

    private fun isBusinessTransaction(request: CreateTransferRequest, accounts: Map<Long, AccountSummary?>): Boolean =
        accounts[request.recipientId]?.business == true || accounts[securityManager.currentUserId()]?.business == true

    private fun validateRequest(
        request: CreateTransferRequest,
        payment: PaymentRequestEntity?,
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

        validatePaymentRequest(request, payment, tenant)
        validateCurrency(request.currency, tenant)
        ensureCurrentUserActive(accounts)
        ensureRecipientActive(request.recipientId, accounts)
    }

    private fun validatePaymentRequest(request: CreateTransferRequest, payment: PaymentRequestEntity?, tenant: Tenant) {
        if (payment != null) {
            if (tenant.id != payment.tenantId)
                throw ForbiddenException(
                    error = Error(
                        code = ErrorURN.ILLEGAL_PAYMENT_REQUEST_ACCESS.urn
                    )
                )

            if (request.recipientId != payment.accountId)
                throw BadRequestException(
                    error = Error(
                        code = ErrorURN.RECIPIENT_NOT_VALID.urn
                    )
                )

            if (request.orderId != payment.orderId)
                throw BadRequestException(
                    error = Error(
                        code = ErrorURN.ORDER_NOT_VALID.urn
                    )
                )

            if (request.amount != payment.amount)
                throw BadRequestException(
                    error = Error(
                        code = ErrorURN.AMOUNT_NOT_VALID.urn
                    )
                )
        }
    }

    private fun validateTransaction(tx: TransactionEntity, payment: PaymentRequestEntity?) {
        ensureBalanceAbove(securityManager.currentUserId(), tx)

        if (payment?.expires != null && payment.expires.isBefore(tx.created))
            throw PaymentException(
                error = com.wutsi.platform.payment.core.Error(
                    code = ErrorCode.EXPIRED,
                    transactionId = tx.id!!
                )
            )
    }
}
