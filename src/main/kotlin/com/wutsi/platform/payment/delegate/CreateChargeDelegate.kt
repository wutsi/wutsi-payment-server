package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.account.dto.Account
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.core.error.ParameterType.PARAMETER_TYPE_PAYLOAD
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodType
import com.wutsi.platform.payment.core.Money
import com.wutsi.platform.payment.core.Status.FAILED
import com.wutsi.platform.payment.core.Status.PENDING
import com.wutsi.platform.payment.core.Status.SUCCESSFUL
import com.wutsi.platform.payment.core.Status.UNKNOWN
import com.wutsi.platform.payment.dao.ChargeRepository
import com.wutsi.platform.payment.dto.CreateChargeRequest
import com.wutsi.platform.payment.dto.CreateChargeResponse
import com.wutsi.platform.payment.entity.ChargeEntity
import com.wutsi.platform.payment.exception.ChargeException
import com.wutsi.platform.payment.model.CreatePaymentRequest
import com.wutsi.platform.payment.model.CreatePaymentResponse
import com.wutsi.platform.payment.model.Party
import com.wutsi.platform.payment.service.AccountService
import com.wutsi.platform.payment.service.ConfigService
import com.wutsi.platform.payment.service.GatewayProvider
import com.wutsi.platform.payment.service.SecurityManager
import com.wutsi.platform.payment.service.SecurityService
import com.wutsi.platform.payment.service.event.ChargeEventPayload
import com.wutsi.platform.payment.service.event.EventURN
import com.wutsi.platform.payment.util.ErrorURN
import org.springframework.stereotype.Service
import java.util.UUID
import javax.transaction.Transactional

@Service
class CreateChargeDelegate(
    private val dao: ChargeRepository,
    private val accountService: AccountService,
    private val securityService: SecurityService,
    private val gatewayProvider: GatewayProvider,
    private val securityManager: SecurityManager,
    private val configService: ConfigService,
    private val eventStream: EventStream
) {
    @Transactional(
        dontRollbackOn = [ChargeException::class]
    )
    fun invoke(request: CreateChargeRequest): CreateChargeResponse {
        val customer = accountService.findAccount(request.customerId, "customerId", PARAMETER_TYPE_PAYLOAD)
        securityManager.checkOwnership(customer)

        // payment method
        val paymentMethod = accountService.findPaymentMethod(request.customerId, request.paymentMethodToken)
        configService.checkSupport(paymentMethod)

        // Create the charge
        val charge = createCharge(request, customer, paymentMethod)

        try {
            // Perform the payment
            val response = createPayment(request, customer, paymentMethod)

            return when (response.status) {
                SUCCESSFUL -> onSuccess(charge, response)
                PENDING -> onPending(charge, response)
                else -> throw IllegalStateException("Unexpected status: ${response.status}")
            }
        } catch (ex: PaymentException) {
            return onFailure(charge, ex)
        }
    }

    private fun createPayment(request: CreateChargeRequest, customer: Account, paymentMethod: PaymentMethod): CreatePaymentResponse {
        val gateway = gatewayProvider.get(PaymentMethodProvider.valueOf(paymentMethod.provider))
        return gateway.createPayment(
            request = CreatePaymentRequest(
                payer = Party(
                    fullName = customer.displayName ?: "",
                    phoneNumber = customer.phone?.number ?: "",
                ),
                amount = Money(
                    value = request.amount,
                    currency = request.currency
                ),
                externalId = request.externalId,
                description = request.description,
                payerMessage = null
            )
        )
    }

    private fun createCharge(request: CreateChargeRequest, customer: Account, paymentMethod: PaymentMethod): ChargeEntity {
        val application = securityService.findApplication(request.applicationId, "applicationId", PARAMETER_TYPE_PAYLOAD)
        val merchant = accountService.findAccount(request.merchantId, "merchantId", PARAMETER_TYPE_PAYLOAD)

        // Create the charge
        return dao.save(
            ChargeEntity(
                id = UUID.randomUUID().toString(),
                customerId = customer.id,
                merchantId = merchant.id,
                applicationId = application.id,
                paymentMethodToken = paymentMethod.token,
                paymentMethodProvider = PaymentMethodProvider.valueOf(paymentMethod.provider),
                paymentMethodType = PaymentMethodType.valueOf(paymentMethod.type),
                description = request.description,
                externalId = request.externalId,
                currency = request.currency,
                amount = request.amount,
                status = UNKNOWN,
                userId = securityManager.currentUserId()!!
            )
        )
    }

    private fun onFailure(charge: ChargeEntity, ex: PaymentException): CreateChargeResponse {
        charge.status = FAILED
        charge.errorCode = ex.error.code
        charge.supplierErrorCode = ex.error.supplierErrorCode
        charge.gatewayTransactionId = ex.error.transactionId
        dao.save(charge)

        eventStream.enqueue(EventURN.CHARGE_FAILED.urn, ChargeEventPayload(charge.id))

        throw ChargeException(
            error = com.wutsi.platform.core.error.Error(
                code = ErrorURN.TRANSACTION_FAILED.urn,
                downstreamCode = charge.errorCode?.name,
                data = mapOf(
                    "id" to charge.id
                )
            ),
            cause = ex
        )
    }

    private fun onSuccess(charge: ChargeEntity, response: CreatePaymentResponse): CreateChargeResponse {
        charge.status = SUCCESSFUL
        charge.gatewayTransactionId = response.transactionId
        charge.financialTransactionId = response.financialTransactionId
        dao.save(charge)

        eventStream.enqueue(EventURN.CHARGE_SUCCESSFUL.urn, ChargeEventPayload(charge.id))

        return CreateChargeResponse(
            id = charge.id,
            status = response.status.name
        )
    }

    private fun onPending(charge: ChargeEntity, response: CreatePaymentResponse): CreateChargeResponse {
        charge.status = PENDING
        charge.gatewayTransactionId = response.transactionId
        dao.save(charge)

        eventStream.enqueue(EventURN.CHARGE_PENDING.urn, ChargeEventPayload(charge.id))

        return CreateChargeResponse(
            id = charge.id,
            status = response.status.name
        )
    }
}
