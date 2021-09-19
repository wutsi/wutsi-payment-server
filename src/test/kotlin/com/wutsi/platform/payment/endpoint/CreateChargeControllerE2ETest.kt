package com.wutsi.platform.payment.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.Account
import com.wutsi.platform.account.dto.GetAccountResponse
import com.wutsi.platform.account.dto.GetPaymentMethodResponse
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.account.dto.Phone
import com.wutsi.platform.payment.Gateway
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodType
import com.wutsi.platform.payment.core.Error
import com.wutsi.platform.payment.core.ErrorCode.EXPIRED
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.ChargeRepository
import com.wutsi.platform.payment.dto.CreateChargeRequest
import com.wutsi.platform.payment.dto.CreateChargeResponse
import com.wutsi.platform.payment.model.CreatePaymentResponse
import com.wutsi.platform.payment.model.GetPaymentResponse
import com.wutsi.platform.payment.service.GatewayProvider
import com.wutsi.platform.security.dto.Application
import com.wutsi.platform.security.dto.GetApplicationResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.jdbc.Sql
import org.springframework.web.client.RestTemplate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CreateChargeControllerE2ETest : AbstractSecuredController() {
    companion object {
        const val CUSTOMER_ID = 1L
        const val MERCHANT_ID = 33L
        const val APPLICATION_ID = 777L
        const val PAYMENT_TOKEN = "xxx-000"
    }

    @LocalServerPort
    public val port: Int = 0

    @MockBean
    private lateinit var accountApi: WutsiAccountApi

    @MockBean
    private lateinit var gatewayProvider: GatewayProvider

    @Autowired
    private lateinit var chargeDao: ChargeRepository

    private lateinit var gateway: Gateway

    private lateinit var url: String
    private lateinit var rest: RestTemplate

    @BeforeEach
    override fun setUp() {
        super.setUp()

        val account = createAccount(CUSTOMER_ID)
        val application = createApplication(APPLICATION_ID)
        val paymentMethod = createMethodPayment(PAYMENT_TOKEN, "+23799505677")
        gateway = mock()

        doReturn(GetAccountResponse(account)).whenever(accountApi).getAccount(any())
        doReturn(GetPaymentMethodResponse(paymentMethod)).whenever(accountApi).getPaymentMethod(any(), any())
        doReturn(GetApplicationResponse(application)).whenever(securityApi).getApplication(any())

        doReturn(gateway).whenever(gatewayProvider).get(any())
        val createPaymentResponse = createCreatePaymentResponse()
        val getPaymentResponse = createGetPaymentResponse()
        doReturn(createPaymentResponse).whenever(gateway).createPayment(any())
        doReturn(getPaymentResponse).whenever(gateway).getPayment(any())

        rest = createResTemplate(listOf("payment-charge"), CUSTOMER_ID)
        url = "http://localhost:$port/v1/charges"
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateChargeControllerE2E.sql"])
    fun success() {
        val request = createCreateChargeRequest()
        rest.postForEntity(url, request, CreateChargeResponse::class.java)

        Thread.sleep(30000)

        val charges = chargeDao.findAll().toList()
        assertEquals(1, charges.size)
        assertEquals(Status.SUCCESSFUL, charges[0].status)
        assertEquals(request.amount, charges[0].amount)
        assertEquals(request.currency, charges[0].currency)
        assertNull(charges[0].errorCode)
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateChargeControllerE2E.sql"])
    fun failure() {
        val ex = PaymentException(
            error = Error(
                code = EXPIRED,
                supplierErrorCode = "TIMEOUT",
                transactionId = "xxxx"
            )
        )
        doThrow(ex).whenever(gateway).getPayment(any())

        val request = createCreateChargeRequest()
        rest.postForEntity(url, request, CreateChargeResponse::class.java)

        Thread.sleep(30000)

        val charges = chargeDao.findAll().toList()
        assertEquals(1, charges.size)
        assertEquals(Status.FAILED, charges[0].status)
        assertEquals(request.amount, charges[0].amount)
        assertEquals(request.currency, charges[0].currency)
        assertEquals(Status.FAILED, charges[0].status)
        assertEquals(ex.error.code, charges[0].errorCode)
        assertEquals(ex.error.supplierErrorCode, charges[0].supplierErrorCode)
    }

    private fun createCreateChargeRequest(merchantId: Long = MERCHANT_ID, customerId: Long = CUSTOMER_ID) = CreateChargeRequest(
        merchantId = merchantId,
        customerId = customerId,
        amount = 100.0,
        currency = "XAF",
        externalId = "urn:order:123",
        description = "This is a nice description",
        paymentMethodToken = "xxxx-xxxxx",
        applicationId = 1L
    )

    private fun createAccount(id: Long, status: String = "ACTIVE") = Account(
        id = id,
        status = status
    )

    private fun createMethodPayment(
        token: String,
        phoneNumber: String = "",
        country: String = "CM",
        paymentMethodProvider: PaymentMethodProvider = PaymentMethodProvider.MTN
    ) = PaymentMethod(
        token = token,
        phone = Phone(
            number = phoneNumber,
            country = country
        ),
        type = PaymentMethodType.MOBILE_PAYMENT.name,
        provider = paymentMethodProvider.name
    )

    private fun createApplication(id: Long, active: Boolean = true) = Application(
        id = id,
        active = active
    )

    private fun createCreatePaymentResponse(status: Status = Status.PENDING) = CreatePaymentResponse(
        transactionId = UUID.randomUUID().toString(),
        status = status
    )

    private fun createGetPaymentResponse(status: Status = Status.SUCCESSFUL) = GetPaymentResponse(
        status = status
    )
}
