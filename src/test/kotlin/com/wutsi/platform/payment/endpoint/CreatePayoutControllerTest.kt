package com.wutsi.platform.payment.endpoint

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.Account
import com.wutsi.platform.account.dto.GetAccountResponse
import com.wutsi.platform.account.dto.GetPaymentMethodResponse
import com.wutsi.platform.account.dto.ListPaymentMethodResponse
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.account.dto.PaymentMethodSummary
import com.wutsi.platform.account.dto.Phone
import com.wutsi.platform.core.error.ErrorResponse
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.Gateway
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodProvider.MTN
import com.wutsi.platform.payment.PaymentMethodProvider.ORANGE
import com.wutsi.platform.payment.PaymentMethodType
import com.wutsi.platform.payment.PaymentMethodType.MOBILE_PAYMENT
import com.wutsi.platform.payment.core.Error
import com.wutsi.platform.payment.core.ErrorCode.EXPIRED
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.core.Status.PENDING
import com.wutsi.platform.payment.core.Status.SUCCESSFUL
import com.wutsi.platform.payment.dao.PayoutRepository
import com.wutsi.platform.payment.dto.CreatePayoutRequest
import com.wutsi.platform.payment.dto.CreatePayoutResponse
import com.wutsi.platform.payment.model.CreateTransferResponse
import com.wutsi.platform.payment.service.GatewayProvider
import com.wutsi.platform.payment.service.event.EventURN
import com.wutsi.platform.payment.service.event.PayoutEventPayload
import com.wutsi.platform.payment.util.ErrorURN
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
import kotlin.test.assertNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/CreatePayoutController.sql"])
public class CreatePayoutControllerTest : AbstractSecuredController() {
    @LocalServerPort
    public val port: Int = 0

    @Autowired
    private lateinit var dao: PayoutRepository

    @MockBean
    private lateinit var accountApi: WutsiAccountApi

    @MockBean
    private lateinit var gatewayProvider: GatewayProvider

    @MockBean
    private lateinit var eventStream: EventStream

    private lateinit var user: Account
    private lateinit var account: Account
    private lateinit var paymentMethod: PaymentMethod
    private lateinit var gateway: Gateway
    private lateinit var transferResponse: CreateTransferResponse

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

        doReturn(GetAccountResponse(account)).whenever(accountApi).getAccount(any())
        doReturn(GetPaymentMethodResponse(paymentMethod)).whenever(accountApi).getPaymentMethod(any(), any())
        doReturn(ListPaymentMethodResponse(listOf(paymentMethodSummary))).whenever(accountApi).listPaymentMethods(any())

        doReturn(gateway).whenever(gatewayProvider).get(any())
        doReturn(transferResponse).whenever(gateway).createTransfer(any())

        rest = createResTemplate(listOf("payment-manage"), user.id)
        url = "http://localhost:$port/v1/payouts"
    }

    @Test
    fun `create PENDING payout`() {
        val request = createCreatePayoutRequest(100)
        val response = rest.postForEntity(url, request, CreatePayoutResponse::class.java)

        assertEquals(200, response.statusCodeValue)
        assertEquals(transferResponse.status.name, response.body.status)

        val payout = dao.findById(response.body.id).get()
        assertEquals(request.accountId, payout.accountId)
        assertEquals(user.id, payout.userId)
        assertEquals(70000.0, payout.amount)
        assertEquals("XAF", payout.currency)
        assertEquals(transferResponse.transactionId, payout.gatewayTransactionId)
        assertEquals(transferResponse.status, payout.status)
        assertEquals(MOBILE_PAYMENT, payout.paymentMethodType)
        assertEquals(MTN, payout.paymentMethodProvider)
        assertEquals(paymentMethod.token, payout.paymentMethodToken)
        assertNull(payout.errorCode)
        assertNull(payout.supplierErrorCode)
        assertNull(payout.financialTransactionId)

        verify(eventStream).enqueue(EventURN.PAYOUT_PENDING.urn, PayoutEventPayload(payout.id))
    }

    @Test
    fun `create SUCCESFUL payout`() {
        transferResponse = createTransferResponse(SUCCESSFUL)
        doReturn(transferResponse).whenever(gateway).createTransfer(any())

        val request = createCreatePayoutRequest(101)
        val response = rest.postForEntity(url, request, CreatePayoutResponse::class.java)

        assertEquals(200, response.statusCodeValue)
        assertEquals(transferResponse.status.name, response.body.status)

        val payout = dao.findById(response.body.id).get()
        assertEquals(request.accountId, payout.accountId)
        assertEquals(user.id, payout.userId)
        assertEquals(1000.0, payout.amount)
        assertEquals("XAF", payout.currency)
        assertEquals(transferResponse.transactionId, payout.gatewayTransactionId)
        assertEquals(transferResponse.status, payout.status)
        assertEquals(MOBILE_PAYMENT, payout.paymentMethodType)
        assertEquals(MTN, payout.paymentMethodProvider)
        assertEquals(paymentMethod.token, payout.paymentMethodToken)
        assertNull(payout.errorCode)
        assertNull(payout.supplierErrorCode)
        assertEquals(transferResponse.financialTransactionId, payout.financialTransactionId)

        verify(eventStream).enqueue(EventURN.PAYOUT_SUCCESSFUL.urn, PayoutEventPayload(payout.id))
    }

    @Test
    fun `create Payout above threshold`() {
        val request = createCreatePayoutRequest(102)
        val response = rest.postForEntity(url, request, CreatePayoutResponse::class.java)

        assertEquals(200, response.statusCodeValue)
        assertEquals(transferResponse.status.name, response.body.status)

        val payout = dao.findById(response.body.id).get()
        assertEquals(request.accountId, payout.accountId)
        assertEquals(user.id, payout.userId)
        assertEquals(1000000.0, payout.amount)
        assertEquals("XAF", payout.currency)
        assertEquals(transferResponse.transactionId, payout.gatewayTransactionId)
        assertEquals(transferResponse.status, payout.status)
        assertEquals(MOBILE_PAYMENT, payout.paymentMethodType)
        assertEquals(MTN, payout.paymentMethodProvider)
        assertEquals(paymentMethod.token, payout.paymentMethodToken)
        assertNull(payout.errorCode)
        assertNull(payout.supplierErrorCode)
        assertNull(payout.financialTransactionId)
    }

    @Test
    fun `create Payout below threshold`() {
        val request = createCreatePayoutRequest(200)
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreatePayoutResponse::class.java)
        }

        assertEquals(400, ex.rawStatusCode)
        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.PAYOUT_AMOUNT_BELOW_THRESHOLD.urn, response.error.code)

        verify(eventStream, never()).enqueue(any(), any())
    }

    @Test
    fun `create Payout without balance`() {
        val request = createCreatePayoutRequest(200, PaymentMethodProvider.ORANGE)
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreatePayoutResponse::class.java)
        }

        assertEquals(404, ex.rawStatusCode)
        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.BALANCE_NOT_FOUND.urn, response.error.code)

        verify(eventStream, never()).enqueue(any(), any())
    }

    @Test
    fun `create FAILURE`() {
        val exception = PaymentException(
            error = Error(
                code = EXPIRED,
                supplierErrorCode = "XXX",
                transactionId = "xxxxxxxx"
            )
        )
        doThrow(exception).whenever(gateway).createTransfer(any())

        val request = createCreatePayoutRequest(201)
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreatePayoutResponse::class.java)
        }

        assertEquals(409, ex.rawStatusCode)
        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.TRANSACTION_FAILED.urn, response.error.code)

        val id = response.error.data?.get("payoutId")?.toString()
        val payout = dao.findById(id).get()
        assertEquals(request.accountId, payout.accountId)
        assertEquals(user.id, payout.userId)
        assertEquals(10000.0, payout.amount)
        assertEquals("XAF", payout.currency)
        assertEquals(exception.error.transactionId, payout.gatewayTransactionId)
        assertEquals(Status.FAILED, payout.status)
        assertEquals(PaymentMethodType.MOBILE_PAYMENT, payout.paymentMethodType)
        assertEquals(paymentMethod.token, payout.paymentMethodToken)
        assertEquals(exception.error.code, payout.errorCode)
        assertEquals(exception.error.supplierErrorCode, payout.supplierErrorCode)
        assertNull(payout.financialTransactionId)

        verify(eventStream).enqueue(EventURN.PAYOUT_FAILED.urn, PayoutEventPayload(payout.id))
    }

    @Test
    fun `create Payout on another user account`() {
        val user = createAccount(777)
        rest = createResTemplate(listOf("payment-manage"), user.id)

        val request = createCreatePayoutRequest(300)
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreatePayoutResponse::class.java)
        }

        assertEquals(403, ex.rawStatusCode)

        verify(eventStream, never()).enqueue(any(), any())
    }

    @Test
    fun `create Payout without valid scope`() {
        rest = createResTemplate(listOf("xxx"), user.id)

        val request = createCreatePayoutRequest(400)
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreatePayoutResponse::class.java)
        }

        assertEquals(403, ex.rawStatusCode)

        verify(eventStream, never()).enqueue(any(), any())
    }

    @Test
    fun `create Payout twice`() {
        account = createAccount(400)
        doReturn(GetAccountResponse(account)).whenever(accountApi).getAccount(400)

        rest = createResTemplate(listOf("payment-manage"), account.id)

        val request = createCreatePayoutRequest(400)
        val response = rest.postForEntity(url, request, CreatePayoutResponse::class.java)

        assertEquals(200, response.statusCodeValue)
        assertEquals("400", response.body.id)
        assertEquals(SUCCESSFUL.name, response.body.status)

        val payout = dao.findById(response.body.id).get()
        assertEquals(400L, payout.accountId)
        assertEquals(400L, payout.userId)
        assertEquals(400.0, payout.amount)
        assertEquals("XAF", payout.currency)
        assertEquals("400-gw", payout.gatewayTransactionId)
        assertEquals(SUCCESSFUL, payout.status)
        assertEquals(MOBILE_PAYMENT, payout.paymentMethodType)
        assertEquals(ORANGE, payout.paymentMethodProvider)
        assertEquals(paymentMethod.token, payout.paymentMethodToken)
        assertNull(payout.errorCode)
        assertNull(payout.supplierErrorCode)
        assertEquals("400-fin", payout.financialTransactionId)

        verify(eventStream, never()).enqueue(any(), any())
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
