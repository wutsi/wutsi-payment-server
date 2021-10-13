package com.wutsi.platform.payment.endpoint

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.Account
import com.wutsi.platform.account.dto.GetAccountResponse
import com.wutsi.platform.account.dto.GetPaymentMethodResponse
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.account.dto.Phone
import com.wutsi.platform.core.error.ErrorResponse
import com.wutsi.platform.core.stream.EventStream
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
import com.wutsi.platform.payment.service.event.ChargeEventPayload
import com.wutsi.platform.payment.service.event.EventURN
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
import org.springframework.test.context.jdbc.Sql
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/CreateChargeController.sql"])
public class CreateChargeControllerTest : AbstractSecuredController() {
    companion object {
        const val CUSTOMER_ID = 1L
        const val MERCHANT_ID = 33L
        const val APPLICATION_ID = 777L
    }

    @LocalServerPort
    public val port: Int = 0

    @Autowired
    private lateinit var dao: ChargeRepository

    @MockBean
    private lateinit var accountApi: WutsiAccountApi

    @MockBean
    private lateinit var gatewayProvider: GatewayProvider

    @MockBean
    private lateinit var eventStream: EventStream

    private lateinit var paymentMethod: PaymentMethod
    private lateinit var application: Application
    private lateinit var gateway: Gateway
    private lateinit var paymentResponse: CreatePaymentResponse

    private lateinit var url: String
    private lateinit var rest: RestTemplate

    @BeforeEach
    override fun setUp() {
        super.setUp()

        application = createApplication(APPLICATION_ID)
        paymentMethod = createMethodPayment()
        paymentResponse = createPaymentResponse()
        gateway = mock()

        doReturn(GetPaymentMethodResponse(paymentMethod)).whenever(accountApi).getPaymentMethod(any(), any())
        doReturn(GetApplicationResponse(application)).whenever(securityApi).getApplication(any())

        doReturn(gateway).whenever(gatewayProvider).get(any())
        doReturn(paymentResponse).whenever(gateway).createPayment(any())

        url = "http://localhost:$port/v1/charges"
    }

    @Test
    fun `charge - PENDING`() {
        val request = createCreateChargeRequest()
        val response = rest.postForEntity(url, request, CreateChargeResponse::class.java)

        assertEquals(200, response.statusCodeValue)

        assertEquals(paymentResponse.status.name, response.body.status)

        val charge = dao.findById(response.body.id).get()
        assertEquals(request.accountId, charge.merchantId)
        assertEquals(CUSTOMER_ID, charge.customerId)
        assertEquals(CUSTOMER_ID, charge.userId)
        assertEquals(request.applicationId, charge.applicationId)
        assertEquals(request.amount, charge.amount)
        assertEquals(request.currency, charge.currency)
        assertEquals(request.description, charge.description)
        assertEquals(request.externalId, charge.externalId)
        assertEquals(paymentResponse.transactionId, charge.gatewayTransactionId)
        assertEquals(paymentResponse.status, charge.status)
        assertNull(charge.errorCode)
        assertNull(charge.supplierErrorCode)

        verify(eventStream).enqueue(EventURN.CHARGE_PENDING.urn, ChargeEventPayload(charge.id))
    }

    @Test
    fun `charge - SUCCESSFULL`() {
        paymentResponse = createPaymentResponse(Status.SUCCESSFUL)
        doReturn(paymentResponse).whenever(gateway).createPayment(any())

        val request = createCreateChargeRequest()
        val response = rest.postForEntity(url, request, CreateChargeResponse::class.java)

        assertEquals(200, response.statusCodeValue)

        assertEquals(paymentResponse.status.name, response.body.status)

        val charge = dao.findById(response.body.id).get()
        assertEquals(request.accountId, charge.merchantId)
        assertEquals(CUSTOMER_ID, charge.customerId)
        assertEquals(CUSTOMER_ID, charge.userId)
        assertEquals(request.applicationId, charge.applicationId)
        assertEquals(request.amount, charge.amount)
        assertEquals(request.currency, charge.currency)
        assertEquals(request.description, charge.description)
        assertEquals(request.externalId, charge.externalId)
        assertEquals(paymentResponse.transactionId, charge.gatewayTransactionId)
        assertEquals(paymentResponse.status, charge.status)
        assertNull(charge.errorCode)
        assertNull(charge.supplierErrorCode)

        verify(eventStream).enqueue(EventURN.CHARGE_SUCCESSFUL.urn, ChargeEventPayload(charge.id))
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
        assertEquals(request.accountId, charge.merchantId)
        assertEquals(CUSTOMER_ID, charge.customerId)
        assertEquals(CUSTOMER_ID, charge.userId)
        assertEquals(request.amount, charge.amount)
        assertEquals(request.currency, charge.currency)
        assertEquals(request.description, charge.description)
        assertEquals(request.externalId, charge.externalId)
        assertEquals(ex.error.transactionId, charge.gatewayTransactionId)
        assertEquals(Status.FAILED, charge.status)
        assertEquals(ex.error.code, charge.errorCode)
        assertEquals(ex.error.supplierErrorCode, charge.supplierErrorCode)

        verify(eventStream).enqueue(EventURN.CHARGE_FAILED.urn, ChargeEventPayload(charge.id))
    }

    @Test
    fun `inactive customer cannot create charges`() {
        val request = createCreateChargeRequest(customerStatus = "SUSPENDED")
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateChargeResponse::class.java)
        }

        assertEquals(409, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.ACCOUNT_NOT_ACTIVE.urn, response.error.code)

        verify(eventStream, never()).enqueue(any(), any())
    }

    @Test
    fun `inactive merchant cannot receive charges`() {
        val request = createCreateChargeRequest(merchantStatus = "SUSPENDED")
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateChargeResponse::class.java)
        }

        assertEquals(409, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.ACCOUNT_NOT_ACTIVE.urn, response.error.code)

        verify(eventStream, never()).enqueue(any(), any())
    }

    @Test
    fun `inactive application cannot create charges`() {
        application = createApplication(APPLICATION_ID, false)
        doReturn(GetApplicationResponse(application)).whenever(securityApi).getApplication(eq(APPLICATION_ID))

        val request = createCreateChargeRequest()
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateChargeResponse::class.java)
        }

        assertEquals(404, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.APPLICATION_NOT_ACTIVE.urn, response.error.code)

        verify(eventStream, never()).enqueue(any(), any())
    }

    @Test
    fun `payment-method not supported`() {
        paymentMethod = createMethodPayment(paymentMethodProvider = PaymentMethodProvider.ORANGE)
        doReturn(GetPaymentMethodResponse(paymentMethod)).whenever(accountApi).getPaymentMethod(any(), any())

        val request = createCreateChargeRequest()
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateChargeResponse::class.java)
        }

        assertEquals(409, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.PAYMENT_METHOD_NOT_SUPPORTED.urn, response.error.code)

        verify(eventStream, never()).enqueue(any(), any())
    }

    @Test
    fun `anonymous cannot create charges`() {
        val request = createCreateChargeRequest()
        val ex = assertThrows<HttpClientErrorException> {
            RestTemplate().postForEntity(url, request, CreateChargeResponse::class.java)
        }

        assertEquals(401, ex.rawStatusCode)

        verify(eventStream, never()).enqueue(any(), any())
    }

    @Test
    fun `user with invalid permission cannot create charges`() {
        val request = createCreateChargeRequest(scopes = listOf("xxx"))
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateChargeResponse::class.java)
        }

        assertEquals(403, ex.rawStatusCode)

        verify(eventStream, never()).enqueue(any(), any())
    }

    private fun createCreateChargeRequest(
        merchantId: Long = MERCHANT_ID,
        customerId: Long = CUSTOMER_ID,
        scopes: List<String> = listOf("payment-manage"),
        customerStatus: String = "ACTIVE",
        merchantStatus: String = "ACTIVE"
    ): CreateChargeRequest {
        val merchant = createAccount(merchantId, merchantStatus)
        val customer = createAccount(customerId, customerStatus)
        doReturn(GetAccountResponse(merchant)).whenever(accountApi).getAccount(merchantId)
        doReturn(GetAccountResponse(customer)).whenever(accountApi).getAccount(customerId)

        rest = createResTemplate(scopes, customerId)

        return CreateChargeRequest(
            accountId = merchantId,
            amount = 10000.0,
            currency = "XAF",
            externalId = "urn:order:123",
            description = "This is a nice description",
            paymentMethodToken = "xxxx-xxxxx",
            applicationId = APPLICATION_ID
        )
    }

    private fun createAccount(id: Long, status: String = "ACTIVE") = Account(
        id = id,
        status = status
    )

    private fun createMethodPayment(
        token: String = "xxxx-xxx",
        phoneNumber: String = "+199505678",
        country: String = "CM",
        paymentMethodProvider: PaymentMethodProvider = PaymentMethodProvider.MTN
    ) = PaymentMethod(
        token = token,
        phone = Phone(
            number = phoneNumber,
            country = country
        ),
        type = PaymentMethodType.MOBILE.name,
        provider = paymentMethodProvider.name
    )

    private fun createApplication(id: Long, active: Boolean = true) = Application(
        id = id,
        active = active
    )

    private fun createPaymentResponse(status: Status = Status.PENDING) = CreatePaymentResponse(
        transactionId = UUID.randomUUID().toString(),
        status = status
    )
}
