package com.wutsi.platform.payment.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.account.dto.Account
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.account.dto.Phone
import com.wutsi.platform.payment.Gateway
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodType
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.ChargeRepository
import com.wutsi.platform.payment.dto.CreateChargeRequest
import com.wutsi.platform.payment.dto.CreateChargeResponse
import com.wutsi.platform.payment.model.CreatePaymentResponse
import com.wutsi.platform.payment.service.AccountService
import com.wutsi.platform.payment.service.GatewayProvider
import com.wutsi.platform.payment.service.SecurityService
import com.wutsi.platform.security.dto.Application
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.web.client.RestTemplate
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CreateChargeControllerTest : AbstractSecuredController() {
    @LocalServerPort
    public val port: Int = 0

    @Autowired
    private lateinit var dao: ChargeRepository

    @MockBean
    private lateinit var accountService: AccountService

    @MockBean
    private lateinit var securityService: SecurityService

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

        customer = createAccount(1)
        user = customer
        merchant = createAccount(11)
        application = createApplication(777)
        paymentMethod = createMethodPayment("xxxxx", "+23799505677")
        paymentResponse = createPaymentResponse()
        gateway = mock()

        doReturn(customer).whenever(accountService).findAccount(eq(customer.id), anyOrNull(), anyOrNull())
        doReturn(merchant).whenever(accountService).findAccount(eq(merchant.id), anyOrNull(), anyOrNull())
        doReturn(application).whenever(securityService).findApplication(any(), anyOrNull(), anyOrNull())
        doReturn(paymentMethod).whenever(accountService).findPaymentMethod(any(), any())

        doReturn(gateway).whenever(gatewayProvider).get(any())
        doReturn(paymentResponse).whenever(gateway).createPayment(any())

        rest = createResTemplate(listOf("payment-charge"), customer.id)
        url = "http://localhost:$port/v1/charges"
    }

    @Test
    fun invoke() {
        val request = CreateChargeRequest(
            merchantId = merchant.id,
            customerId = customer.id,
            amount = 100.0,
            currency = "XAF",
            externalId = "urn:order:123",
            description = "This is a nice description",
            paymentMethodToken = paymentMethod.token,
            applicationId = application.id
        )
        val response = rest.postForEntity(url, request, CreateChargeResponse::class.java)

        assertEquals(200, response.statusCodeValue)

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
        assertEquals(user.id, charge.userId)
    }

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
