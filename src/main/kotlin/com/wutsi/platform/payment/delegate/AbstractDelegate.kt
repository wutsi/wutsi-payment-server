package com.wutsi.platform.payment.delegate

import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.Parameter
import com.wutsi.platform.core.error.ParameterType.PARAMETER_TYPE_PAYLOAD
import com.wutsi.platform.core.error.exception.BadRequestException
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.dao.BalanceRepository
import com.wutsi.platform.payment.entity.BalanceEntity
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.event.TransactionEventPayload
import com.wutsi.platform.payment.util.ErrorURN
import com.wutsi.platform.tenant.dto.Tenant
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AbstractDelegate(
    protected val accountApi: WutsiAccountApi,
    protected val balanceDao: BalanceRepository,
    protected val eventStream: EventStream,
) {
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
                )
            )
        } catch (ex: Exception) {
            LoggerFactory.getLogger(this::class.java).error("Unable to publish event $type", ex)
        }
    }

}
