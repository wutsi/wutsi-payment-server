package com.wutsi.platform.payment.delegate

import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.AccountSummary
import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.Parameter
import com.wutsi.platform.core.error.ParameterType.PARAMETER_TYPE_PAYLOAD
import com.wutsi.platform.core.error.exception.BadRequestException
import com.wutsi.platform.core.error.exception.ConflictException
import com.wutsi.platform.core.error.exception.ForbiddenException
import com.wutsi.platform.core.logging.KVLogger
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.core.ErrorCode
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.BalanceRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.entity.BalanceEntity
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.error.ErrorURN
import com.wutsi.platform.payment.error.TransactionException
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.event.TransactionEventPayload
import com.wutsi.platform.payment.service.SecurityManager
import com.wutsi.platform.tenant.dto.Tenant
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AbstractDelegate {
    @Autowired
    protected lateinit var accountApi: WutsiAccountApi

    @Autowired
    protected lateinit var balanceDao: BalanceRepository

    @Autowired
    protected lateinit var transactionDao: TransactionRepository

    @Autowired
    protected lateinit var securityManager: SecurityManager

    @Autowired
    protected lateinit var logger: KVLogger

    @Autowired
    protected lateinit var eventStream: EventStream

    @Transactional
    open fun onError(tx: TransactionEntity, ex: PaymentException) {
        tx.status = Status.FAILED
        tx.errorCode = ex.error.code.name
        tx.supplierErrorCode = ex.error.supplierErrorCode
        tx.gatewayTransactionId = ex.error.transactionId
        tx.gatewayFees = 0.0
        transactionDao.save(tx)

        publish(EventURN.TRANSACTION_FAILED, tx)
    }

    protected fun createTransactionException(
        tx: TransactionEntity,
        error: ErrorURN,
        ex: PaymentException
    ): TransactionException =
        TransactionException(
            error = Error(
                code = error.urn,
                downstreamCode = ex.error.code.name,
                data = mapOf(
                    "transaction-id" to tx.id!!
                )
            )
        )

    protected fun log(ex: PaymentException) {
        logger.add("gateway_error_code", ex.error.code)
        logger.add("gateway_supplier_error_code", ex.error.supplierErrorCode)
    }

    protected fun validateCurrency(currency: String, tenant: Tenant) {
        if (currency != tenant.currency) {
            throw BadRequestException(
                error = Error(
                    code = ErrorURN.CURRENCY_NOT_SUPPORTED.urn,
                    parameter = Parameter(
                        type = PARAMETER_TYPE_PAYLOAD,
                        name = "currency",
                        value = currency
                    )
                )
            )
        }
    }

    protected fun ensureCurrentUserActive(accounts: Map<Long, AccountSummary>) {
        val user = accounts[securityManager.currentUserId()]!!
        ensureAccountActive(user.id, user.status, ErrorURN.USER_NOT_ACTIVE)
    }

    protected fun ensureRecipientActive(recipientId: Long, accounts: Map<Long, AccountSummary>) {
        val user = accounts[recipientId]!!
        ensureAccountActive(user.id, user.status, ErrorURN.RECIPIENT_NOT_ACTIVE)
    }

    @Deprecated("")
    protected fun ensureCurrentUserActive() {
        val userId = securityManager.currentUserId()
        val user = accountApi.getAccount(userId).account
        ensureAccountActive(user.id, user.status, ErrorURN.USER_NOT_ACTIVE)
    }

    @Deprecated("")
    protected fun ensureRecipientActive(recipientId: Long) {
        val user = accountApi.getAccount(recipientId).account
        ensureAccountActive(user.id, user.status, ErrorURN.RECIPIENT_NOT_ACTIVE)
    }

    protected fun ensureAccountActive(id: Long, status: String, error: ErrorURN) {
        if (!status.equals("ACTIVE", ignoreCase = true)) {
            throw ForbiddenException(
                error = Error(
                    code = error.urn,
                    data = mapOf("userId" to id)
                ),
            )
        }
    }

    protected fun ensureBalanceAbove(userId: Long, tx: TransactionEntity) {
        val balance = balanceDao.findByAccountId(userId)
            .orElseThrow {
                ConflictException(
                    error = Error(
                        code = ErrorURN.TRANSACTION_FAILED.urn,
                        downstreamCode = ErrorCode.NOT_ENOUGH_FUNDS.name
                    )
                )
            }

        if (balance.amount < tx.amount)
            throw PaymentException(
                error = com.wutsi.platform.payment.core.Error(
                    code = ErrorCode.NOT_ENOUGH_FUNDS,
                )
            )
    }

    protected fun updateBalance(userId: Long, amount: Double, tenant: Tenant): BalanceEntity {
        val balance = balanceDao.findByAccountId(userId)
            .orElseGet {
                balanceDao.save(
                    BalanceEntity(
                        accountId = userId,
                        tenantId = tenant.id,
                        currency = tenant.currency
                    )
                )
            }

        balance.amount += amount
        return balanceDao.save(balance)
    }

    protected fun publish(type: EventURN, tx: TransactionEntity) {
        try {
            eventStream.publish(
                type.urn,
                TransactionEventPayload(
                    transactionId = tx.id!!,
                    type = tx.type.name,
                    orderId = tx.orderId
                )
            )
        } catch (ex: Exception) {
            LoggerFactory.getLogger(this::class.java).error("Unable to publish event $type", ex)
        }
    }

    protected fun log(tx: TransactionEntity) {
        logger.add("transaction_id", tx.id)
        logger.add("transaction_amount", tx.amount)
        logger.add("transaction_fees", tx.fees)
        logger.add("transaction_net", tx.net)
        logger.add("transaction_gateway_fees", tx.gatewayFees)
    }
}
