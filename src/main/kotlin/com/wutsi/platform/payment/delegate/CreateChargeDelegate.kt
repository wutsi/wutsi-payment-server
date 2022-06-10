package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.account.dto.Account
import com.wutsi.platform.account.dto.AccountSummary
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.account.dto.SearchAccountRequest
import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.exception.ForbiddenException
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
class CreateChargeDelegate(
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
        logger.add("idempotency_key", request.idempotencyKey)

        // Idempotency
        val opt = transactionDao.findByIdempotencyKey(request.idempotencyKey)
        if (opt.isPresent) {
            val tx = opt.get()
            log(tx)
            logger.add("idempotency_hit", true)

            checkIdempotency(request, tx)
            if (tx.status == Status.FAILED)
                throw createTransactionException(tx, ErrorURN.TRANSACTION_FAILED, tx.errorCode)

            return CreateChargeResponse(
                id = tx.id!!,
                status = tx.status.name
            )
        } else {
            logger.add("idempotency_hit", false)
        }

        // Validate the request
        val tenant = tenantProvider.get()
        val accounts = accountApi.searchAccount(
            request = SearchAccountRequest(
                ids = listOfNotNull(request.recipientId, securityManager.currentUserId())
            )
        ).accounts.map { it.id to it }.toMap()
        validateRequest(request, tenant, accounts)

        // Gateway
        val userId = securityManager.currentUserId()!!
        val paymentMethod = accountApi.getPaymentMethod(
            id = userId,
            token = request.paymentMethodToken
        ).paymentMethod

        // Create transaction
        val payer = accountApi.getAccount(userId).account
        val tx = createTransaction(request, paymentMethod, tenant, payer, accounts)

        // Perform the charge
        try {
            // Charge
            val response = charge(tx, paymentMethod, request, payer)
            log(response)

            if (response.status == Status.SUCCESSFUL) {
                onSuccess(tx, response, tenant)
            } else {
                onPending(tx, response.transactionId)
            }

            return CreateChargeResponse(
                id = tx.id!!,
                status = tx.status.name
            )
        } catch (ex: PaymentException) {
            log(ex)
            onError(tx, ex, tenant)
            throw createTransactionException(tx, ErrorURN.TRANSACTION_FAILED, ex)
        } finally {
            log(tx)
        }
    }

    fun onSuccess(
        tx: TransactionEntity,
        response: CreatePaymentResponse,
        tenant: Tenant
    ) {
        if (tx.status == Status.SUCCESSFUL)
            return

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
        val gateway = gatewayProvider.get(PaymentMethodProvider.valueOf(paymentMethod.provider))
        logger.add("gateway", gateway::class.java.simpleName)

        return gateway.createPayment(
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
    }

    private fun createTransaction(
        request: CreateChargeRequest,
        paymentMethod: PaymentMethod,
        tenant: Tenant,
        payer: Account,
        accounts: Map<Long, AccountSummary>
    ): TransactionEntity {
        val tx = TransactionEntity(
            id = UUID.randomUUID().toString(),
            accountId = payer.id,
            recipientId = request.recipientId,
            tenantId = tenant.id,
            paymentMethodToken = request.paymentMethodToken,
            paymentMethodProvider = PaymentMethodProvider.valueOf(paymentMethod.provider),
            type = TransactionType.CHARGE,
            amount = request.amount,
            currency = tenant.currency,
            created = OffsetDateTime.now(),
            description = request.description,
            orderId = request.orderId,
            idempotencyKey = request.idempotencyKey,
            business = accounts[request.recipientId]?.business ?: false
        )
        feesCalculator.apply(tx, paymentMethod?.type, tenant)
        return transactionDao.save(tx)
    }

    private fun validateRequest(request: CreateChargeRequest, tenant: Tenant, accounts: Map<Long, AccountSummary>) {
        validateCurrency(request.currency, tenant)
        ensureCurrentUserActive(accounts)
        ensureRecipientValid(request.recipientId, accounts)
        ensureRecipientActive(request.recipientId, accounts)
        ensureBusinessAccount(request.recipientId, accounts)
    }

    private fun checkIdempotency(request: CreateChargeRequest, tx: TransactionEntity) {
        val matches = request.idempotencyKey == tx.idempotencyKey &&
            request.amount == tx.amount &&
            request.currency == tx.currency &&
            request.orderId == tx.orderId &&
            request.recipientId == tx.recipientId &&
            request.paymentMethodToken == tx.paymentMethodToken &&
            securityManager.currentUserId() == tx.accountId &&
            TransactionType.CHARGE == tx.type

        if (!matches)
            throw ForbiddenException(
                error = Error(
                    code = ErrorURN.IDEMPOTENCY_MISMATCH.urn
                )
            )
    }
}
