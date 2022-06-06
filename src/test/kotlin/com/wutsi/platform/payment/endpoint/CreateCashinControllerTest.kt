package com.wutsi.platform.payment.endpoint

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.account.dto.AccountSummary
import com.wutsi.platform.account.dto.SearchAccountResponse
import com.wutsi.platform.core.error.ErrorResponse
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.core.Error
import com.wutsi.platform.payment.core.ErrorCode.NOT_ENOUGH_FUNDS
import com.wutsi.platform.payment.core.Money
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.BalanceRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.CreateCashinRequest
import com.wutsi.platform.payment.dto.CreateCashinResponse
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.error.ErrorURN
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.event.TransactionEventPayload
import com.wutsi.platform.payment.model.CreatePaymentResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.jdbc.Sql
import org.springframework.web.client.HttpClientErrorException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/CreateCashinController.sql"])
public class CreateCashinControllerTest : AbstractSecuredController() {
    @LocalServerPort
    public val port: Int = 0

    private lateinit var url: String

    @Autowired
    private lateinit var txDao: TransactionRepository

    @Autowired
    private lateinit var balanceDao: BalanceRepository

    @MockBean
    private lateinit var eventStream: EventStream

    @BeforeEach
    override fun setUp() {
        super.setUp()

        url = "http://localhost:$port/v1/transactions/cashins"

        val account = AccountSummary(
            id = USER_ID,
            displayName = user.displayName,
            status = user.status,
        )
        doReturn(SearchAccountResponse(listOf(account))).whenever(accountApi).searchAccount(any())
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateCashinController.sql"])
    fun success() {
        // GIVEN
        val gwFees = 100.0
        val paymentResponse = CreatePaymentResponse("111", "222", Status.SUCCESSFUL, Money(gwFees, "XAF"))
        doReturn(paymentResponse).whenever(gateway).createPayment(any())

        // WHEN
        val request = CreateCashinRequest(
            paymentMethodToken = "11111",
            amount = 50000.0,
            currency = "XAF",
        )
        val response = rest.postForEntity(url, request, CreateCashinResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        assertEquals(Status.SUCCESSFUL.name, response.body!!.status)

        val tx = txDao.findById(response.body!!.id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.accountId)
        assertEquals(request.currency, tx.currency)
        assertEquals(request.amount, tx.amount)
        assertEquals(0.0, tx.fees)
        assertEquals(gwFees, tx.gatewayFees)
        assertEquals(request.amount, tx.net)
        assertEquals(request.paymentMethodToken, tx.paymentMethodToken)
        assertEquals(PaymentMethodProvider.MTN, tx.paymentMethodProvider)
        assertEquals(TransactionType.CASHIN, tx.type)
        assertEquals(Status.SUCCESSFUL, tx.status)
        assertEquals(paymentResponse.transactionId, tx.gatewayTransactionId)
        assertEquals(paymentResponse.financialTransactionId, tx.financialTransactionId)
        assertNull(tx.supplierErrorCode)
        assertNull(tx.description)
        assertNull(tx.errorCode)
        assertNull(tx.orderId)

        val balance = balanceDao.findByAccountId(USER_ID).get()
        assertEquals(request.amount, balance.amount)
        assertEquals(request.currency, balance.currency)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_SUCCESSFUL.urn), payload.capture())
        assertEquals(TransactionType.CASHIN.name, payload.firstValue.type)
        assertEquals(tx.id, payload.firstValue.transactionId)
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateCashinController.sql"])
    fun pending() {
        // GIVEN
        val gwFees = 100.0
        val paymentResponse = CreatePaymentResponse("111", null, Status.PENDING, Money(gwFees, "XAF"))
        doReturn(paymentResponse).whenever(gateway).createPayment(any())

        // WHEN
        val request = CreateCashinRequest(
            paymentMethodToken = "11111",
            amount = 50000.0,
            currency = "XAF"
        )
        val response = rest.postForEntity(url, request, CreateCashinResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        assertEquals(Status.PENDING.name, response.body!!.status)

        val tx = txDao.findById(response.body!!.id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.accountId)
        assertEquals(request.currency, tx.currency)
        assertEquals(request.amount, tx.amount)
        assertEquals(0.0, tx.fees)
        assertEquals(request.amount, tx.net)
        assertEquals(0.0, tx.gatewayFees)
        assertEquals(request.paymentMethodToken, tx.paymentMethodToken)
        assertEquals(PaymentMethodProvider.MTN, tx.paymentMethodProvider)
        assertEquals(TransactionType.CASHIN, tx.type)
        assertEquals(Status.PENDING, tx.status)
        assertEquals(paymentResponse.transactionId, tx.gatewayTransactionId)
        assertNull(tx.financialTransactionId)
        assertNull(tx.supplierErrorCode)
        assertNull(tx.description)
        assertNull(tx.errorCode)

        val balance = balanceDao.findByAccountId(USER_ID)
        assertFalse(balance.isPresent)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_PENDING.urn), payload.capture())
        assertEquals(TransactionType.CASHIN.name, payload.firstValue.type)
        assertEquals(tx.id, payload.firstValue.transactionId)
    }

    @Test
    fun failure() {
        // GIVEN
        val e =
            PaymentException(error = Error(code = NOT_ENOUGH_FUNDS, transactionId = "111", supplierErrorCode = "xxxx"))
        doThrow(e).whenever(gateway).createPayment(any())

        // WHEN
        val request = CreateCashinRequest(
            paymentMethodToken = "11111",
            amount = 50000.0,
            currency = "XAF"
        )
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateCashinResponse::class.java)
        }

        // THEN
        assertEquals(409, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.TRANSACTION_FAILED.urn, response.error.code)
        assertEquals(e.error.code.name, response.error.downstreamCode)

        val txId = response.error.data?.get("transaction-id").toString()
        val tx = txDao.findById(txId).get()
        assertEquals(USER_ID, tx.accountId)
        assertEquals(request.currency, tx.currency)
        assertEquals(request.amount, tx.amount)
        assertEquals(0.0, tx.fees)
        assertEquals(0.0, tx.gatewayFees)
        assertEquals(request.amount, tx.net)
        assertEquals(request.paymentMethodToken, tx.paymentMethodToken)
        assertEquals(PaymentMethodProvider.MTN, tx.paymentMethodProvider)
        assertEquals(TransactionType.CASHIN, tx.type)
        assertEquals(Status.FAILED, tx.status)
        assertNull(tx.financialTransactionId)
        assertNull(tx.description)
        assertEquals(e.error.supplierErrorCode, tx.supplierErrorCode)
        assertEquals(e.error.code.name, tx.errorCode)
        assertEquals(e.error.transactionId, tx.gatewayTransactionId)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_FAILED.urn), payload.capture())
        assertEquals(TransactionType.CASHIN.name, payload.firstValue.type)
        assertEquals(tx.id, payload.firstValue.transactionId)
    }

    @Test
    fun badCurrency() {
        // WHEN
        val request = CreateCashinRequest(
            paymentMethodToken = "11111",
            amount = 50000.0,
            currency = "EUR"
        )
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateCashinResponse::class.java)
        }

        // THEN
        assertEquals(400, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.CURRENCY_NOT_SUPPORTED.urn, response.error.code)

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    fun suspendedUser() {
        // GIVEN

        val account = AccountSummary(
            id = USER_ID,
            displayName = user.displayName,
            status = "SUSPENDED",
        )
        doReturn(SearchAccountResponse(listOf(account))).whenever(accountApi).searchAccount(any())

        // WHEN
        val request = CreateCashinRequest(
            paymentMethodToken = "11111",
            amount = 50000.0,
            currency = "EUR"
        )
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateCashinResponse::class.java)
        }

        // THEN
        assertEquals(403, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.USER_NOT_ACTIVE.urn, response.error.code)

        verify(eventStream, never()).publish(any(), any())
    }
}
