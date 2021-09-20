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
import com.wutsi.platform.account.dto.ListPaymentMethodResponse
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.account.dto.PaymentMethodSummary
import com.wutsi.platform.account.dto.Phone
import com.wutsi.platform.payment.Gateway
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodProvider.MTN
import com.wutsi.platform.payment.PaymentMethodType
import com.wutsi.platform.payment.PaymentMethodType.MOBILE_PAYMENT
import com.wutsi.platform.payment.core.Error
import com.wutsi.platform.payment.core.ErrorCode.EXPIRED
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.core.Status.PENDING
import com.wutsi.platform.payment.core.Status.SUCCESSFUL
import com.wutsi.platform.payment.dao.PayoutRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.CreateChargeResponse
import com.wutsi.platform.payment.dto.CreatePayoutRequest
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.model.CreateTransferResponse
import com.wutsi.platform.payment.model.GetTransferResponse
import com.wutsi.platform.payment.service.GatewayProvider
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

    @MockBean
    private lateinit var gatewayProvider: GatewayProvider

    private lateinit var user: Account
    private lateinit var account: Account
    private lateinit var paymentMethod: PaymentMethod
    private lateinit var gateway: Gateway
    private lateinit var transferResponse: CreateTransferResponse
    private lateinit var getTransferResponse: GetTransferResponse

    private lateinit var url: String
    private lateinit var rest: RestTemplate

    @BeforeEach
    override fun setUp() {
        super.setUp()

        account = createAccount(100)
        user = account
        paymentMethod = createMethodPayment("xxxx", "+23799505677")
        val paymentMethodSummary = createMethodPaymentSummary(paymentMethod.token)
        gateway = mock()
        transferResponse = createTransferResponse(PENDING)
        getTransferResponse = createGetTransferResponse(SUCCESSFUL)

        doReturn(GetAccountResponse(account)).whenever(accountApi).getAccount(any())
        doReturn(GetPaymentMethodResponse(paymentMethod)).whenever(accountApi).getPaymentMethod(any(), any())
        doReturn(ListPaymentMethodResponse(listOf(paymentMethodSummary))).whenever(accountApi).listPaymentMethods(any())

        doReturn(gateway).whenever(gatewayProvider).get(any())
        doReturn(transferResponse).whenever(gateway).createTransfer(any())
        doReturn(getTransferResponse).whenever(gateway).getTransfer(any())

        rest = createResTemplate(listOf("payment-manage"), account.id)
        url = "http://localhost:$port/v1/payouts"
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreatePayoutControllerE2E.sql"])
    fun success() {
        val request = createCreatePayoutRequest(100)
        rest.postForEntity(url, request, CreateChargeResponse::class.java)

        Thread.sleep(30000)

        val payouts = dao.findAll().toList()
        assertEquals(1, payouts.size)
        assertEquals(Status.SUCCESSFUL, payouts[0].status)
        assertEquals(70000.0, payouts[0].amount)
        assertEquals("XAF", payouts[0].currency)
        assertNull(payouts[0].errorCode)

        val txs = txDao.findAll().toList()
        assertEquals(1, txs.size)
        assertEquals(TransactionType.PAYOUT, txs[0].type)
        assertEquals(-70000.0, txs[0].amount)
        assertEquals("XAF", txs[0].currency)
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreatePayoutControllerE2E.sql"])
    fun failure() {
        val ex = PaymentException(
            error = Error(
                code = EXPIRED,
                supplierErrorCode = "TIMEOUT",
                transactionId = "xxxx"
            )
        )
        doThrow(ex).whenever(gateway).getTransfer(any())

        val request = createCreatePayoutRequest(100)
        rest.postForEntity(url, request, CreateChargeResponse::class.java)

        Thread.sleep(30000)

        val payouts = dao.findAll().toList()
        assertEquals(1, payouts.size)
        assertEquals(Status.FAILED, payouts[0].status)
        assertEquals(70000.0, payouts[0].amount)
        assertEquals("XAF", payouts[0].currency)
        assertEquals(Status.FAILED, payouts[0].status)
        assertEquals(ex.error.code, payouts[0].errorCode)
        assertEquals(ex.error.supplierErrorCode, payouts[0].supplierErrorCode)

        val txs = txDao.findAll().toList()
        assertTrue(txs.isEmpty())
    }

    private fun createCreatePayoutRequest(accountId: Long, paymentMethodProvider: PaymentMethodProvider = MTN) = CreatePayoutRequest(
        accountId = accountId,
        paymentMethodProvider = paymentMethodProvider.name
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
        type = MOBILE_PAYMENT.name,
        provider = paymentMethodProvider.name
    )

    private fun createTransferResponse(status: Status = PENDING) = CreateTransferResponse(
        transactionId = UUID.randomUUID().toString(),
        status = status
    )

    private fun createGetTransferResponse(status: Status = PENDING) = GetTransferResponse(
        status = status
    )

    private fun createMethodPaymentSummary(
        token: String,
        type: PaymentMethodType = MOBILE_PAYMENT,
        paymentMethodProvider: PaymentMethodProvider = MTN
    ) = PaymentMethodSummary(
        token = token,
        type = type.name,
        provider = paymentMethodProvider.name
    )
}
