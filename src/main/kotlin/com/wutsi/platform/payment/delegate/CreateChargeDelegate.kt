package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.account.dto.Account
import com.wutsi.platform.account.dto.AccountSummary
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.account.dto.SearchAccountRequest
import com.wutsi.platform.payment.GatewayProvider
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.core.Money
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dto.CreateChargeRequest
import com.wutsi.platform.payment.dto.CreateChargeResponse
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.error.ErrorURN
import com.wutsi.platform.payment.error.TransactionException
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.model.CreatePaymentRequest
import com.wutsi.platform.payment.model.CreatePaymentResponse
import com.wutsi.platform.payment.model.Party
import com.wutsi.platform.payment.service.FeesCalculator
import com.wutsi.platform.payment.service.TenantProvider
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
public class CreateChargeDelegate(
    private val tenantProvider: TenantProvider,
    private val gatewayProvider: GatewayProvider,
    private val feesCalculator: FeesCalculator,
) : AbstractDelegate() {
    @Transactional(noRollbackFor = [TransactionException::class])
    fun invoke(request: CreateChargeRequest): CreateChargeResponse {
        logger.add("currency", request.currency)
        logger.add("amount", request.amount)
        logger.add("payment_token", request.paymentMethodToken)
        logger.add("recipient_id", request.recipientId)
        logger.add("description", request.description)
        logger.add("order_id", request.orderId)

        val payer = accountApi.getAccount(securityManager.currentUserId()).account
        val tenant = tenantProvider.get()
        val accounts = accountApi.searchAccount(
            request = SearchAccountRequest(
                ids = listOfNotNull(request.recipientId, securityManager.currentUserId())
            )
        ).accounts.map { it.id to it }.toMap()

        // Validate the request
        validateRequest(request, tenant, accounts)

        // Gateway
        val paymentMethod = accountApi.getPaymentMethod(
            id = securityManager.currentUserId(),
            token = request.paymentMethodToken
        ).paymentMethod

        // Create transaction
        val tx = createTransaction(request, paymentMethod, tenant)

        // Perform the charge
        try {
            val response = charge(tx, paymentMethod, request, payer)
            logger.add("gateway_status", response.status)
            logger.add("gateway_transaction_id", response.transactionId)

            if (response.status == Status.SUCCESSFUL) {
                onSuccess(tx, response, tenant)
            } else {
                onPending(tx)
            }

            return CreateChargeResponse(
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

    @Transactional
    fun onSuccess(
        tx: TransactionEntity,
        response: CreatePaymentResponse,
        tenant: Tenant
    ) {
        // Update balance
        updateBalance(tx.recipientId!!, tx.net, tenant)

        // Update transaction
        tx.status = Status.SUCCESSFUL
        tx.gatewayTransactionId = response.transactionId
        tx.financialTransactionId = response.financialTransactionId
        tx.gatewayFees = response.fees.value
        transactionDao.save(tx)

        publish(EventURN.TRANSACTION_SUCCESSFUL, tx)
    }

    private fun charge(
        tx: TransactionEntity,
        paymentMethod: PaymentMethod,
        request: CreateChargeRequest,
        payer: Account
    ): CreatePaymentResponse {
        val paymentGateway = gatewayProvider.get(PaymentMethodProvider.valueOf(paymentMethod.provider))
        val response = paymentGateway.createPayment(
            CreatePaymentRequest(
                payer = Party(
                    fullName = paymentMethod.ownerName,
                    phoneNumber = paymentMethod.phone!!.number,
                    country = paymentMethod.phone!!.country,
                    email = payer.email ?: ""
                ),
                amount = Money(tx.amount, tx.currency),
                externalId = tx.id!!,
                description = request.description ?: "",
                payerMessage = null
            )
        )

        return response
    }

    private fun createTransaction(
        request: CreateChargeRequest,
        paymentMethod: PaymentMethod,
        tenant: Tenant,
    ): TransactionEntity {
        val fees = feesCalculator.compute(TransactionType.CHARGE, request.amount, tenant)
        return transactionDao.save(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                accountId = securityManager.currentUserId(),
                recipientId = request.recipientId,
                tenantId = tenant.id,
                paymentMethodToken = request.paymentMethodToken,
                paymentMethodProvider = PaymentMethodProvider.valueOf(paymentMethod.provider),
                type = TransactionType.CHARGE,
                amount = request.amount,
                fees = fees,
                net = request.amount - fees,
                currency = tenant.currency,
                status = Status.PENDING,
                created = OffsetDateTime.now(),
                description = request.description
            )
        )
    }

    private fun validateRequest(request: CreateChargeRequest, tenant: Tenant, accounts: Map<Long, AccountSummary>) {
        validateCurrency(request.currency, tenant)
        ensureCurrentUserActive(accounts)
        ensureRecipientActive(request.recipientId, accounts)
        ensureBusinessAccount(request.recipientId, accounts)
    }
}
