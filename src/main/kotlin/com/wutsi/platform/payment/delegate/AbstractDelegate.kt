package com.wutsi.platform.payment.delegate

import com.wutsi.platform.account.WutsiAccountApi
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
import com.wutsi.platform.payment.dao.BalanceRepository
import com.wutsi.platform.payment.entity.BalanceEntity
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.event.TransactionEventPayload
import com.wutsi.platform.payment.service.SecurityManager
import com.wutsi.platform.payment.util.ErrorURN
import com.wutsi.platform.tenant.dto.Tenant
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class AbstractDelegate {
    @Autowired
    protected lateinit var accountApi: WutsiAccountApi

    @Autowired
    protected lateinit var balanceDao: BalanceRepository

    @Autowired
    protected lateinit var securityManager: SecurityManager

    @Autowired
    protected lateinit var logger: KVLogger

    @Autowired
    protected lateinit var eventStream: EventStream

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

    protected fun ensureCurrentUserActive() {
        val userId = securityManager.currentUserId()
        val user = accountApi.getAccount(userId).account
        if (!user.status.equals("ACTIVE", ignoreCase = true)) {
            throw ForbiddenException(
                error = Error(
                    code = ErrorURN.USER_NOT_ACTIVE.urn,
                    data = mapOf("userId" to userId)
                ),
            )
        }
    }

    protected fun ensureBalanceAbove(userId: Long, threshold: Double, tenant: Tenant) {
        val balance = balanceDao.findByUserIdAndTenantId(userId, tenant.id)
            .orElseThrow {
                ConflictException(
                    error = Error(
                        code = ErrorURN.TRANSACTION_FAILED.urn,
                        downstreamCode = ErrorCode.NOT_ENOUGH_FUNDS.name
                    )
                )
            }

        if (balance.amount < threshold)
            throw ConflictException(
                error = Error(
                    code = ErrorURN.TRANSACTION_FAILED.urn,
                    downstreamCode = ErrorCode.NOT_ENOUGH_FUNDS.name
                )
            )
    }

    protected fun updateBalance(userId: Long, amount: Double, tenant: Tenant): BalanceEntity {
        val balance = balanceDao.findByUserIdAndTenantId(userId, tenant.id)
            .orElseGet {
                balanceDao.save(
                    BalanceEntity(
                        userId = userId,
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
                    tenantId = tx.tenantId,
                    transactionId = tx.id!!,
                    type = tx.type.name,
                    amount = tx.amount,
                    currency = tx.currency,
                    userId = tx.userId,
                    recipientId = tx.recipientId
                )
            )
        } catch (ex: Exception) {
            LoggerFactory.getLogger(this::class.java).error("Unable to publish event $type", ex)
        }
    }
}
