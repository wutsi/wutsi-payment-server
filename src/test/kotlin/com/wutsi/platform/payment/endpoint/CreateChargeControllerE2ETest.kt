package com.wutsi.platform.payment.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.Account
import com.wutsi.platform.account.dto.GetAccountResponse
import com.wutsi.platform.account.dto.GetPaymentMethodResponse
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.account.dto.Phone
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodType
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.ChargeRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.CreateChargeRequest
import com.wutsi.platform.payment.dto.CreateChargeResponse
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.security.dto.Application
import com.wutsi.platform.security.dto.GetApplicationResponse
import jdk.nashorn.internal.ir.annotations.Ignore
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Autowired
    private lateinit var chargeDao: ChargeRepository

    @Autowired
    private lateinit var txDao: TransactionRepository

    private lateinit var url: String
    private lateinit var rest: RestTemplate

    @BeforeEach
    override fun setUp() {
        super.setUp()

        createApplication(APPLICATION_ID)

        url = "http://localhost:$port/v1/charges"
    }

    @Ignore
    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateChargeControllerE2E.sql"])
    fun success() {
        createPaymentMethod(PAYMENT_TOKEN, "+23799505677")

        val request = createCreateChargeRequest()
        rest.postForEntity(url, request, CreateChargeResponse::class.java)

        Thread.sleep(30000)

        val charges = chargeDao.findAll().toList()
        assertEquals(1, charges.size)
        assertEquals(Status.SUCCESSFUL, charges[0].status)
        assertEquals(request.amount, charges[0].amount)
        assertEquals(request.currency, charges[0].currency)
        assertNull(charges[0].errorCode)

        val txs = txDao.findAll().toList()
        assertEquals(2, txs.size)
        assertEquals(TransactionType.CHARGE, txs[0].type)
        assertEquals(request.accountId, txs[0].accountId)
        assertEquals(9800.0, txs[0].amount)
        assertEquals(request.currency, txs[0].currency)

        assertEquals(TransactionType.FEES, txs[1].type)
        assertEquals(-100, txs[1].accountId)
        assertEquals(200.0, txs[1].amount)
        assertEquals(request.currency, txs[1].currency)
    }

    @Ignore
    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateChargeControllerE2E.sql"])
    fun failure() {
        createPaymentMethod(PAYMENT_TOKEN, "46733123452")

        val request = createCreateChargeRequest()
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateChargeResponse::class.java)
        }

        assertEquals(409, ex.rawStatusCode)

        val charges = chargeDao.findAll().toList()
        assertEquals(1, charges.size)
        assertEquals(Status.FAILED, charges[0].status)
        assertEquals(request.amount, charges[0].amount)
        assertEquals(request.currency, charges[0].currency)
        assertNotNull(charges[0].errorCode)
        assertNotNull(charges[0].supplierErrorCode)

        val txs = txDao.findAll().toList()
        assertTrue(txs.isEmpty())
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateChargeControllerE2E.sql"])
    fun pending() {
        createPaymentMethod(PAYMENT_TOKEN, "46733123454")

        val request = createCreateChargeRequest()
        rest.postForEntity(url, request, CreateChargeResponse::class.java)

        val charges = chargeDao.findAll().toList()
        assertEquals(1, charges.size)
        assertEquals(Status.PENDING, charges[0].status)
        assertEquals(request.amount, charges[0].amount)
        assertEquals(request.currency, charges[0].currency)
        assertNull(charges[0].errorCode)
        assertNull(charges[0].supplierErrorCode)

        val txs = txDao.findAll().toList()
        assertTrue(txs.isEmpty())
    }

    private fun createCreateChargeRequest(
        merchantId: Long = MERCHANT_ID,
        customerId: Long = CUSTOMER_ID,
        scopes: List<String> = listOf("payment-manage")
    ): CreateChargeRequest {
        val merchant = createAccount(merchantId)
        val customer = createAccount(customerId)
        doReturn(GetAccountResponse(merchant)).whenever(accountApi).getAccount(merchantId)
        doReturn(GetAccountResponse(customer)).whenever(accountApi).getAccount(customerId)

        rest = createResTemplate(scopes, customerId)

        return CreateChargeRequest(
            accountId = merchantId,
            amount = 10000.0,
            currency = "XAF",
            externalId = "123344",
            description = "This is a nice description",
            paymentMethodToken = "xxxx-xxxxx",
            applicationId = 1L
        )
    }

    private fun createAccount(id: Long, status: String = "ACTIVE") = Account(
        id = id,
        status = status
    )

    private fun createPaymentMethod(
        token: String,
        phoneNumber: String,
        country: String = "CM",
        paymentMethodProvider: PaymentMethodProvider = PaymentMethodProvider.MTN
    ): PaymentMethod {
        val paymentMethod = PaymentMethod(
            token = token,
            phone = Phone(
                number = phoneNumber,
                country = country
            ),
            type = PaymentMethodType.MOBILE_PAYMENT.name,
            provider = paymentMethodProvider.name
        )

        doReturn(GetPaymentMethodResponse(paymentMethod)).whenever(accountApi).getPaymentMethod(any(), any())

        return paymentMethod
    }

    private fun createApplication(id: Long, active: Boolean = true): Application {
        val application = Application(
            id = id,
            active = active
        )
        doReturn(GetApplicationResponse(application)).whenever(securityApi).getApplication(any())
        return application
    }
}
