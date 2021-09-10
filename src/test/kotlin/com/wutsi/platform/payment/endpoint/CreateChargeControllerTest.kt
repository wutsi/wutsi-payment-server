package com.wutsi.platform.payment.endpoint

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.Account
import com.wutsi.platform.account.dto.GetAccountResponse
import com.wutsi.platform.account.dto.GetPaymentMethodResponse
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.account.dto.Phone
import com.wutsi.platform.core.error.ErrorResponse
import com.wutsi.platform.payment.Gateway
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodType
import com.wutsi.platform.payment.core.Error
import com.wutsi.platform.payment.core.ErrorCode
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.ChargeRepository
import com.wutsi.platform.payment.dto.CreateChargeRequest
import com.wutsi.platform.payment.dto.CreateChargeResponse
import com.wutsi.platform.payment.model.CreatePaymentResponse
import com.wutsi.platform.payment.service.GatewayProvider
import com.wutsi.platform.payment.util.ErrorURN
import com.wutsi.platform.security.dto.Application
import com.wutsi.platform.security.dto.GetApplicationResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CreateChargeControllerTest : AbstractSecuredController() {
    companion object {
        const val CUSTOMER_ID = 1L
        const val MERCHANT_ID = 33L
        const val APPLICATION_ID = 777L
        const val PAYMENT_TOKEN = "xxx-000"
    }

    @LocalServerPort
    public val port: Int = 0

    @Autowired
    private lateinit var dao: ChargeRepository

    @MockBean
    private lateinit var accountApi: WutsiAccountApi

    @MockBean
    private lateinit var gatewayProvider: GatewayProvider

    private lateinit var user: Account
    private lateinit var customer: Account
    private lateinit var merchant: Account
    private lateinit var paymentMethod: PaymentMethod
    private lateinit var application: Application
    private lateinit var gateway: Gateway
    private lateinit var paymentResponse: CreatePaymentResponse

    private lateinit var url: String
    private lateinit var rest: RestTemplate

    @BeforeEach
    override fun setUp() {
        super.setUp()

        customer = createAccount(CUSTOMER_ID)
        user = customer
        merchant = createAccount(MERCHANT_ID)
        application = createApplication(APPLICATION_ID)
        paymentMethod = createMethodPayment(PAYMENT_TOKEN, "+23799505677")
        paymentResponse = createPaymentResponse()
        gateway = mock()

        doReturn(GetAccountResponse(customer)).whenever(accountApi).getAccount(eq(CUSTOMER_ID))
        doReturn(GetAccountResponse(merchant)).whenever(accountApi).getAccount(eq(MERCHANT_ID))
        doReturn(GetPaymentMethodResponse(paymentMethod)).whenever(accountApi).getPaymentMethod(CUSTOMER_ID, PAYMENT_TOKEN)
        doReturn(GetApplicationResponse(application)).whenever(securityApi).getApplication(APPLICATION_ID)

        doReturn(gateway).whenever(gatewayProvider).get(any())
        doReturn(paymentResponse).whenever(gateway).createPayment(any())

        rest = createResTemplate(listOf("payment-charge"), customer.id)
        url = "http://localhost:$port/v1/charges"
    }

    @Test
    fun charge() {
        val request = createCreateChargeRequest()
        val response = rest.postForEntity(url, request, CreateChargeResponse::class.java)

        assertEquals(200, response.statusCodeValue)

        assertEquals(paymentResponse.status.name, response.body.status)

        val charge = dao.findById(response.body.id).get()
        assertEquals(request.merchantId, charge.merchantId)
        assertEquals(request.applicationId, charge.applicationId)
        assertEquals(request.customerId, charge.customerId)
        assertEquals(request.amount, charge.amount)
        assertEquals(request.currency, charge.currency)
        assertEquals(request.description, charge.description)
        assertEquals(request.externalId, charge.externalId)
        assertEquals(paymentResponse.transactionId, charge.gatewayTransactionId)
        assertEquals(paymentResponse.status, charge.status)
        assertNull(charge.errorCode)
        assertNull(charge.supplierErrorCode)
        assertEquals(user.id, charge.userId)
    }

    @Test
    fun `charge with inactive user`() {
        customer = createAccount(CUSTOMER_ID, "suspended")
        doReturn(GetAccountResponse(customer)).whenever(accountApi).getAccount(eq(CUSTOMER_ID))

        val request = createCreateChargeRequest()
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateChargeResponse::class.java)
        }

        assertEquals(404, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.ACCOUNT_NOT_ACTIVE.urn, response.error.code)
    }

    @Test
    fun `charge with inactive application`() {
        application = createApplication(APPLICATION_ID, false)
        doReturn(GetApplicationResponse(application)).whenever(securityApi).getApplication(eq(APPLICATION_ID))

        val request = createCreateChargeRequest()
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateChargeResponse::class.java)
        }

        assertEquals(404, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.APPLICATION_NOT_ACTIVE.urn, response.error.code)
    }

    @Test
    fun `charge with a another user`() {
        rest = createResTemplate(listOf("payment-charge"), 7777L)

        val request = createCreateChargeRequest()
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateChargeResponse::class.java)
        }

        assertEquals(403, ex.rawStatusCode)
    }

    @Test
    fun `charge with PaymentException`() {
        val ex = PaymentException(
            error = Error(
                code = ErrorCode.NOT_ENOUGH_FUNDS,
                transactionId = UUID.randomUUID().toString(),
                supplierErrorCode = "xxxx"
            )
        )
        doThrow(ex).whenever(gateway).createPayment(any())

        val request = createCreateChargeRequest()
        val ex1 = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateChargeResponse::class.java)
        }

        assertEquals(409, ex1.rawStatusCode)
        val response = ObjectMapper().readValue(ex1.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.TRANSACTION_FAILED.urn, response.error.code)
        assertEquals(ex.error.code.name, response.error.downstreamCode)
        assertNotNull(response.error.data?.get("id"))

        val id = response.error.data?.get("id") as String
        val charge = dao.findById(id).get()
        assertEquals(request.merchantId, charge.merchantId)
        assertEquals(request.applicationId, charge.applicationId)
        assertEquals(request.customerId, charge.customerId)
        assertEquals(request.amount, charge.amount)
        assertEquals(request.currency, charge.currency)
        assertEquals(request.description, charge.description)
        assertEquals(request.externalId, charge.externalId)
        assertEquals(ex.error.transactionId, charge.gatewayTransactionId)
        assertEquals(Status.STATUS_FAILED, charge.status)
        assertEquals(ex.error.code, charge.errorCode)
        assertEquals(ex.error.supplierErrorCode, charge.supplierErrorCode)
        assertEquals(user.id, charge.userId)
    }

    private fun createCreateChargeRequest(merchantId: Long = MERCHANT_ID, customerId: Long = CUSTOMER_ID) = CreateChargeRequest(
        merchantId = merchantId,
        customerId = customerId,
        amount = 100.0,
        currency = "XAF",
        externalId = "urn:order:123",
        description = "This is a nice description",
        paymentMethodToken = paymentMethod.token,
        applicationId = application.id
    )

    private fun createAccount(id: Long, status: String = "active") = Account(
        id = id,
        status = status
    )

    private fun createMethodPayment(token: String, phoneNumber: String = "") = PaymentMethod(
        token = token,
        phone = Phone(
            number = phoneNumber
        ),
        type = PaymentMethodType.PAYMENT_METHOD_TYPE_MOBILE_PAYMENT.shortName,
        provider = PaymentMethodProvider.PAYMENT_METHOD_PROVIDER_MTN.shortName
    )

    private fun createApplication(id: Long, active: Boolean = true) = Application(
        id = id,
        active = active
    )

    private fun createPaymentResponse() = CreatePaymentResponse(
        transactionId = UUID.randomUUID().toString(),
        status = Status.STATUS_PENDING
    )
}
