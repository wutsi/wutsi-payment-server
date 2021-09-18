package com.wutsi.platform.payment.service

import com.wutsi.platform.account.dto.Account
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.Parameter
import com.wutsi.platform.core.error.exception.NotFoundException
import com.wutsi.platform.core.error.exception.WutsiException
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodType
import com.wutsi.platform.payment.core.Money
import com.wutsi.platform.payment.core.Status.FAILED
import com.wutsi.platform.payment.core.Status.PENDING
import com.wutsi.platform.payment.core.Status.SUCCESSFUL
import com.wutsi.platform.payment.dao.BalanceRepository
import com.wutsi.platform.payment.dao.PayoutRepository
import com.wutsi.platform.payment.entity.BalanceEntity
import com.wutsi.platform.payment.entity.ConfigEntity
import com.wutsi.platform.payment.entity.PayoutEntity
import com.wutsi.platform.payment.exception.PayoutException
import com.wutsi.platform.payment.model.CreateTransferRequest
import com.wutsi.platform.payment.model.CreateTransferResponse
import com.wutsi.platform.payment.model.GetTransferResponse
import com.wutsi.platform.payment.model.Party
import com.wutsi.platform.payment.service.event.EventURN
import com.wutsi.platform.payment.service.event.PayoutEventPayload
import com.wutsi.platform.payment.util.ErrorURN
import org.springframework.stereotype.Service
import java.lang.Double.min
import javax.transaction.Transactional

@Service
public class PayoutService(
    private val balanceDao: BalanceRepository,
    private val dao: PayoutRepository,
    private val configs: ConfigService,
    private val accountService: AccountService,
    private val securityManager: SecurityManager,
    private val paymentGatewayProvider: GatewayProvider,
    private val eventStream: EventStream
) {
    @Throws(PayoutException::class)
    @Transactional(
        dontRollbackOn = [PayoutException::class]
    )
    fun payout(accountId: Long, paymentMethodProvider: PaymentMethodProvider): PayoutEntity {
        val account = getAccount(accountId)
        val paymentMethod = getPaymentMethodForPayout(accountId, paymentMethodProvider)
        val config = getConfig(paymentMethodProvider, paymentMethod.phone?.country)

        // Balance
        val balance = balanceDao.findByAccountIdAndPaymentMethodProvider(accountId, paymentMethodProvider).get()
        if (balance.amount < config.payoutMinValue)
            throw PayoutException(
                error = Error(
                    code = ErrorURN.PAYOUT_AMOUNT_BELOW_THRESHOLD.urn,
                    data = mapOf(
                        "accountId" to accountId,
                        "paymentMethodProvider" to paymentMethodProvider
                    )
                )
            )

        // Create the payout
        val payout = createPayout(balance, config, account, paymentMethod)

        // Perform the transaction
        return try {
            val response = createTransfer(payout, account, paymentMethod)
            when (response.status) {
                SUCCESSFUL -> onSuccess(payout, response)
                PENDING -> onPending(payout, response)
                else -> throw IllegalStateException("Unexpected status: ${response.status}")
            }
        } catch (ex: PaymentException) {
            onFailure(payout, ex)
        }
    }

    @Throws(PayoutException::class)
    @Transactional(
        dontRollbackOn = [PayoutException::class]
    )
    fun sync(payoutId: String): PayoutEntity {
        val payout = dao.findById(payoutId)
            .orElseThrow {
                NotFoundException(
                    error = Error(
                        code = ErrorURN.PAYOUT_NOT_FOUND.urn,
                        parameter = Parameter(
                            value = payoutId
                        )
                    )
                )
            }

        val gateway = paymentGatewayProvider.get(payout.paymentMethodProvider)
        return try {
            val response = gateway.getTransfer(payout.gatewayTransactionId)
            when (response.status) {
                SUCCESSFUL -> onSuccess(payout, response)
                PENDING -> payout
                else -> throw IllegalStateException("Unexpected status: ${response.status}")
            }
        } catch (ex: PaymentException) {
            onFailure(payout, ex)
        }
    }

    private fun getAccount(accountId: Long): Account =
        try {
            accountService.findAccount(accountId)
        } catch (ex: WutsiException) {
            throw PayoutException(
                error = ex.error,
                ex
            )
        }

    private fun getConfig(paymentMethodProvider: PaymentMethodProvider, country: String?): ConfigEntity =
        try {
            configs.getConfig(paymentMethodProvider, country)
        } catch (ex: WutsiException) {
            throw PayoutException(
                error = ex.error,
                ex
            )
        }

    private fun getPaymentMethodForPayout(accountId: Long, paymentMethodProvider: PaymentMethodProvider): PaymentMethod =
        try {
            accountService.findPaymentMethodForPayout(accountId, paymentMethodProvider)
        } catch (ex: WutsiException) {
            throw PayoutException(
                error = ex.error,
                ex
            )
        }

    private fun createPayout(balance: BalanceEntity, config: ConfigEntity, account: Account, paymentMethod: PaymentMethod) =
        dao.save(
            PayoutEntity(
                id = balance.payoutId,
                accountId = balance.accountId,
                paymentMethodType = PaymentMethodType.valueOf(paymentMethod.type),
                paymentMethodProvider = PaymentMethodProvider.valueOf(paymentMethod.provider),
                currency = balance.currency,
                amount = min(balance.amount, config.payoutMaxValue),
                status = PENDING,
                userId = securityManager.currentUserId()
            )
        )

    private fun createTransfer(payout: PayoutEntity, account: Account, paymentMethod: PaymentMethod): CreateTransferResponse {
        val gateway = paymentGatewayProvider.get(PaymentMethodProvider.valueOf(paymentMethod.provider))
        return gateway.createTransfer(
            request = CreateTransferRequest(
                payee = Party(
                    fullName = account.displayName ?: "",
                    phoneNumber = paymentMethod.phone?.number ?: ""
                ),
                amount = Money(
                    value = payout.amount,
                    currency = payout.currency
                ),
                externalId = payout.id,
                description = payout.description ?: "",
                payerMessage = null
            )
        )
    }

    private fun onSuccess(payout: PayoutEntity, response: CreateTransferResponse): PayoutEntity {
        payout.status = SUCCESSFUL
        payout.gatewayTransactionId = response.transactionId
        payout.financialTransactionId = response.financialTransactionId
        dao.save(payout)

        eventStream.enqueue(EventURN.PAYOUT_SUCCESSFUL.urn, PayoutEventPayload(payout.id))

        return payout
    }

    private fun onSuccess(payout: PayoutEntity, response: GetTransferResponse): PayoutEntity {
        payout.status = SUCCESSFUL
        payout.financialTransactionId = response.financialTransactionId
        dao.save(payout)

        eventStream.enqueue(EventURN.PAYOUT_SUCCESSFUL.urn, PayoutEventPayload(payout.id))

        return payout
    }

    private fun onPending(payout: PayoutEntity, response: CreateTransferResponse): PayoutEntity {
        payout.status = PENDING
        payout.gatewayTransactionId = response.transactionId
        dao.save(payout)

        eventStream.enqueue(EventURN.PAYOUT_PENDING.urn, PayoutEventPayload(payout.id))

        return payout
    }

    private fun onFailure(payout: PayoutEntity, ex: PaymentException): PayoutEntity {
        payout.status = FAILED
        payout.errorCode = ex.error.code
        payout.supplierErrorCode = ex.error.supplierErrorCode
        payout.gatewayTransactionId = ex.error.transactionId
        dao.save(payout)

        eventStream.enqueue(EventURN.PAYOUT_FAILED.urn, PayoutEventPayload(payout.id))

        throw PayoutException(
            error = Error(
                code = ErrorURN.TRANSACTION_FAILED.urn,
                downstreamCode = payout.errorCode?.name,
                data = mapOf(
                    "payoutId" to payout.id,
                    "accountId" to payout.accountId
                )
            )
        )
    }
}
