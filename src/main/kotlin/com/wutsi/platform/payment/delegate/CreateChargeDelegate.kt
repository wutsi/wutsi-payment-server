package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.core.error.ParameterType.PARAMETER_TYPE_PAYLOAD
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodType
import com.wutsi.platform.payment.core.Error
import com.wutsi.platform.payment.core.Money
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.core.Status.STATUS_FAILED
import com.wutsi.platform.payment.dao.ChargeRepository
import com.wutsi.platform.payment.dto.CreateChargeRequest
import com.wutsi.platform.payment.dto.CreateChargeResponse
import com.wutsi.platform.payment.entity.ChargeEntity
import com.wutsi.platform.payment.exception.ChargeException
import com.wutsi.platform.payment.model.CreatePaymentRequest
import com.wutsi.platform.payment.model.Party
import com.wutsi.platform.payment.service.AccountService
import com.wutsi.platform.payment.service.GatewayProvider
import com.wutsi.platform.payment.service.SecurityManager
import com.wutsi.platform.payment.service.SecurityService
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
    private val securityManager: SecurityManager
) {
    @Transactional(
        dontRollbackOn = [ChargeException::class]
    )
    fun invoke(request: CreateChargeRequest): CreateChargeResponse {
        val customer = accountService.findAccount(request.customerId, "customerId", PARAMETER_TYPE_PAYLOAD)
        securityManager.checkOwnership(customer)

        val application = securityService.findApplication(request.applicationId, "applicationId", PARAMETER_TYPE_PAYLOAD)
        val merchant = accountService.findAccount(request.merchantId, "merchantId", PARAMETER_TYPE_PAYLOAD)
        val paymentMethod = accountService.findPaymentMethod(request.customerId, request.paymentMethodToken)
        val gateway = gatewayProvider.get(paymentMethod.provider)

        // Perform the payment
        var status: Status
        var transactionId: String
        var error: Error? = null
        try {
            val response = gateway.createPayment(
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
            status = response.status
            transactionId = response.transactionId
        } catch (ex: PaymentException) {
            status = STATUS_FAILED
            transactionId = ex.error.transactionId
            error = ex.error
        }

        // Save the charge
        val charge = dao.save(
            ChargeEntity(
                id = UUID.randomUUID().toString(),
                customerId = customer.id,
                merchantId = merchant.id,
                applicationId = application.id,
                paymentMethodToken = paymentMethod.token,
                paymentMethodProvider = PaymentMethodProvider.values().find { it.shortName == paymentMethod.provider }!!,
                paymentMethodType = PaymentMethodType.values().find { it.shortName == paymentMethod.type }!!,
                description = request.description,
                externalId = request.externalId,
                currency = request.currency,
                amount = request.amount,
                gatewayTransactionId = transactionId,
                status = status,
                errorCode = error?.code,
                supplierErrorCode = error?.supplierErrorCode,
                userId = securityManager.currentUserId()
            )
        )

        // Throw exception on failure
        if (status == STATUS_FAILED)
            throw ChargeException(
                error = com.wutsi.platform.core.error.Error(
                    code = ErrorURN.TRANSACTION_FAILED.urn,
                    downstreamCode = error?.code?.name,
                    data = mapOf(
                        "id" to charge.id
                    )
                )
            )

        // Return success
        return CreateChargeResponse(
            id = charge.id,
            status = status.name
        )
    }
}
