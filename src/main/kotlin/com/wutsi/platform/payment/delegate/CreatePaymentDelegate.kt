package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.account.dto.AccountSummary
import com.wutsi.platform.account.dto.SearchAccountRequest
import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.Parameter
import com.wutsi.platform.core.error.ParameterType
import com.wutsi.platform.core.error.exception.ForbiddenException
import com.wutsi.platform.core.error.exception.NotFoundException
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.core.ErrorCode
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.PaymentRequestRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.CreatePaymentRequest
import com.wutsi.platform.payment.dto.CreatePaymentResponse
import com.wutsi.platform.payment.entity.PaymentRequestEntity
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.error.ErrorURN
import com.wutsi.platform.payment.error.TransactionException
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.service.FeesCalculator
import com.wutsi.platform.payment.service.TenantProvider
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

@Service
public class CreatePaymentDelegate(
    private val dao: PaymentRequestRepository,
    private val transactionDao: TransactionRepository,
    private val tenantProvider: TenantProvider,
    private val feesCalculator: FeesCalculator,
) : AbstractDelegate() {

    public fun invoke(request: CreatePaymentRequest): CreatePaymentResponse {
        logger.add("request_id", request.requestId)

        val paymentRequest = dao.findById(request.requestId)
            .orElseThrow {
                NotFoundException(
                    error = Error(
                        code = ErrorURN.PAYMENT_REQUEST_NOT_FOUND.urn,
                        parameter = Parameter(
                            name = "requestId",
                            value = request.requestId,
                            type = ParameterType.PARAMETER_TYPE_PAYLOAD
                        )
                    )
                )
            }
        val tenant = tenantProvider.get()
        val accounts = accountApi.searchAccount(
            request = SearchAccountRequest(
                ids = listOfNotNull(paymentRequest.accountId, securityManager.currentUserId())
            )
        ).accounts.map { it.id to it }.toMap()
        validateRequest(paymentRequest, tenant, accounts)

        val tx = createTransaction(paymentRequest, tenant, accounts)
        try {
            validateTransaction(paymentRequest, tx)

            onSuccess(paymentRequest, tx, tenant)
            return CreatePaymentResponse(
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

    private fun onSuccess(request: PaymentRequestEntity, tx: TransactionEntity, tenant: Tenant) {
        updateBalance(securityManager.currentUserId(), -tx.amount, tenant)
        updateBalance(request.accountId, tx.net, tenant)

        publish(EventURN.TRANSACTION_SUCCESSFULL, tx)
    }

    private fun onFailure(tx: TransactionEntity, ex: PaymentException) {
        tx.status = Status.FAILED
        tx.errorCode = ex.error.code.name
        transactionDao.save(tx)
        publish(EventURN.TRANSACTION_FAILED, tx)
    }

    private fun createTransaction(
        request: PaymentRequestEntity,
        tenant: Tenant,
        accounts: Map<Long, AccountSummary?>
    ): TransactionEntity {
        val recipient = accounts[request.accountId]

        val tx = transactionDao.save(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                accountId = securityManager.currentUserId(),
                recipientId = request.accountId,
                tenantId = request.tenantId,
                type = TransactionType.PAYMENT,
                amount = request.amount,
                fees = 0.0,
                net = request.amount,
                currency = request.currency,
                status = Status.SUCCESSFUL,
                created = OffsetDateTime.now(),
                description = request.description,
                paymentRequestId = request.id,
                business = recipient?.business ?: false,
                retail = recipient?.retail ?: false,
            )
        )
        feesCalculator.computeFees(tx, tenant, accounts)

        logger.add("transaction_id", tx.id)
        return tx
    }

    private fun validateRequest(request: PaymentRequestEntity, tenant: Tenant, accounts: Map<Long, AccountSummary>) {
        if (tenant.id != request.tenantId)
            throw ForbiddenException(
                error = Error(
                    code = ErrorURN.ILLEGAL_TENANT_ACCESS.urn
                )
            )

        if (request.accountId == securityManager.currentUserId())
            throw ForbiddenException(
                error = Error(
                    code = ErrorURN.SELF_TRANSACTION_ERROR.urn
                )
            )

        val recipient = accounts[request.accountId]!!
        if (!recipient.business)
            throw ForbiddenException(
                error = Error(
                    code = ErrorURN.RESTRICTED_TO_BUSINESS_ACCOUNT.urn
                )
            )

        validateCurrency(request.currency, tenant)

        val userId = securityManager.currentUserId()
        ensureAccountActive(userId, accounts[userId]!!.status, ErrorURN.USER_NOT_ACTIVE)
        ensureAccountActive(recipient.id, recipient.status, ErrorURN.RECIPIENT_NOT_ACTIVE)
    }

    private fun validateTransaction(request: PaymentRequestEntity, tx: TransactionEntity) {
        if (request.expires != null && request.expires.isBefore(tx.created))
            throw PaymentException(
                error = com.wutsi.platform.payment.core.Error(
                    code = ErrorCode.EXPIRED,
                    transactionId = tx.id!!
                )
            )

        ensureBalanceAbove(securityManager.currentUserId(), tx)
    }
}
