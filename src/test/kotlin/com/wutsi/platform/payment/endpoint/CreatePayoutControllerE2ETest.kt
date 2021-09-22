package com.wutsi.platform.payment.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.Account
import com.wutsi.platform.account.dto.GetAccountResponse
import com.wutsi.platform.account.dto.GetPaymentMethodResponse
import com.wutsi.platform.account.dto.ListPaymentMethodResponse
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.account.dto.PaymentMethodSummary
import com.wutsi.platform.account.dto.Phone
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodProvider.MTN
import com.wutsi.platform.payment.PaymentMethodType
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.core.Status.FAILED
import com.wutsi.platform.payment.dao.PayoutRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.CreateChargeResponse
import com.wutsi.platform.payment.dto.CreatePayoutRequest
import com.wutsi.platform.payment.entity.TransactionType
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
public class CreatePayoutControllerE2ETest : AbstractSecuredController() {
    @LocalServerPort
    public val port: Int = 0

    @Autowired
    private lateinit var dao: PayoutRepository

    @Autowired
    private lateinit var txDao: TransactionRepository

    @MockBean
    private lateinit var accountApi: WutsiAccountApi

    private lateinit var account: Account

    private lateinit var url: String
    private lateinit var rest: RestTemplate

    @BeforeEach
    override fun setUp() {
        super.setUp()

        account = createAccount(100)

        rest = createResTemplate(listOf("payment-manage"), account.id)
        url = "http://localhost:$port/v1/payouts"
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreatePayoutControllerE2E.sql"])
    fun success() {
        createPaymentMethod("xxxx", "+23799505677")

        val request = createCreatePayoutRequest(100)
        rest.postForEntity(url, request, CreateChargeResponse::class.java)

        Thread.sleep(30000)

        val payouts = dao.findAll().toList()
        assertEquals(1, payouts.size)
        assertEquals(Status.SUCCESSFUL, payouts[0].status)
        assertEquals(70000.0, payouts[0].amount)
        assertEquals("XAF", payouts[0].currency)
        assertNull(payouts[0].errorCode)

        val txs = txDao.findByReferenceId(payouts[0].id)
        assertEquals(1, txs.size)
        assertEquals(TransactionType.PAYOUT, txs[0].type)
        assertEquals(-70000.0, txs[0].amount)
        assertEquals("XAF", txs[0].currency)
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreatePayoutControllerE2E.sql"])
    fun failure() {
        createPaymentMethod("xxxx", "+46733123452")

        val request = createCreatePayoutRequest(100)
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateChargeResponse::class.java)
        }

        assertEquals(409, ex.rawStatusCode)

        val payouts = dao.findAll().toList()
        assertEquals(1, payouts.size)
        assertEquals(FAILED, payouts[0].status)
        assertEquals(70000.0, payouts[0].amount)
        assertEquals("XAF", payouts[0].currency)
        assertEquals(FAILED, payouts[0].status)
        assertNotNull(payouts[0].errorCode)
        assertNotNull(payouts[0].supplierErrorCode)

        val txs = txDao.findAll().toList()
        assertTrue(txs.isEmpty())
    }

    private fun createCreatePayoutRequest(
        accountId: Long,
        paymentMethodProvider: PaymentMethodProvider = MTN,
        scopes: List<String> = listOf("payment-manage")
    ): CreatePayoutRequest {
        val account = createAccount(accountId)
        doReturn(GetAccountResponse(account)).whenever(accountApi).getAccount(accountId)

        rest = createResTemplate(scopes, account.id)
        return CreatePayoutRequest(paymentMethodProvider.name)
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

        val summary = PaymentMethodSummary(
            token = token,
            type = PaymentMethodType.MOBILE_PAYMENT.name,
            provider = paymentMethodProvider.name
        )
        doReturn(ListPaymentMethodResponse(listOf(summary))).whenever(accountApi).listPaymentMethods(any())

        return paymentMethod
    }
}
