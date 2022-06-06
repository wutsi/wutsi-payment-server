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
import com.wutsi.platform.account.entity.AccountStatus
import com.wutsi.platform.core.error.ErrorResponse
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.core.Error
import com.wutsi.platform.payment.core.ErrorCode
import com.wutsi.platform.payment.core.Money
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.BalanceRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.CreateCashinResponse
import com.wutsi.platform.payment.dto.CreateChargeRequest
import com.wutsi.platform.payment.dto.CreateChargeResponse
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
import kotlin.test.assertNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CreateChargeControllerTest : AbstractSecuredController() {
    companion object {
        const val RECIPIENT_ID = 100L
    }

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

        url = "http://localhost:$port/v1/transactions/charges"

        val accounts = listOf(
            AccountSummary(id = USER_ID, status = AccountStatus.ACTIVE.name, business = false),
            AccountSummary(id = RECIPIENT_ID, status = AccountStatus.ACTIVE.name, business = true)
        )
        doReturn(SearchAccountResponse(accounts)).whenever(accountApi).searchAccount(any())
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateChargeController.sql"])
    fun success() {
        // GIVEN
        val gwFees = 100.0
        val paymentResponse = CreatePaymentResponse("111", "222", Status.SUCCESSFUL, Money(gwFees, "XAF"))
        doReturn(paymentResponse).whenever(gateway).createPayment(any())

        // WHEN
        val request = createRequest()
        val response = rest.postForEntity(url, request, CreateChargeResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        assertEquals(Status.SUCCESSFUL.name, response.body!!.status)

        val fees = 5000.0
        val tx = txDao.findById(response.body!!.id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.accountId)
        assertEquals(RECIPIENT_ID, tx.recipientId)
        assertEquals(request.currency, tx.currency)
        assertEquals(request.amount, tx.amount)
        assertEquals(fees, tx.fees)
        assertEquals(gwFees, tx.gatewayFees)
        assertEquals(request.amount - fees, tx.net)
        assertEquals(request.paymentMethodToken, tx.paymentMethodToken)
        assertEquals(PaymentMethodProvider.MTN, tx.paymentMethodProvider)
        assertEquals(TransactionType.CHARGE, tx.type)
        assertEquals(Status.SUCCESSFUL, tx.status)
        assertEquals(paymentResponse.transactionId, tx.gatewayTransactionId)
        assertEquals(paymentResponse.financialTransactionId, tx.financialTransactionId)
        assertNull(tx.supplierErrorCode)
        assertEquals(request.description, tx.description)
        assertNull(tx.errorCode)

        assertEquals(5000.0, balanceDao.findByAccountId(USER_ID).get().amount)
        assertEquals(100000.0 + tx.net, balanceDao.findByAccountId(RECIPIENT_ID).get().amount)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_SUCCESSFUL.urn), payload.capture())
        assertEquals(TransactionType.CHARGE.name, payload.firstValue.type)
        assertEquals(tx.id, payload.firstValue.transactionId)
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateChargeController.sql"])
    fun pending() {
        // GIVEN
        val gwFees = 100.0
        val paymentResponse = CreatePaymentResponse("111", "222", Status.PENDING, Money(gwFees, "XAF"))
        doReturn(paymentResponse).whenever(gateway).createPayment(any())

        // WHEN
        val request = createRequest()
        val response = rest.postForEntity(url, request, CreateChargeResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        assertEquals(Status.PENDING.name, response.body!!.status)

        val fees = 5000.0
        val tx = txDao.findById(response.body!!.id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.accountId)
        assertEquals(RECIPIENT_ID, tx.recipientId)
        assertEquals(request.currency, tx.currency)
        assertEquals(request.amount, tx.amount)
        assertEquals(fees, tx.fees)
        assertEquals(0.0, tx.gatewayFees)
        assertEquals(request.amount - fees, tx.net)
        assertEquals(request.paymentMethodToken, tx.paymentMethodToken)
        assertEquals(PaymentMethodProvider.MTN, tx.paymentMethodProvider)
        assertEquals(TransactionType.CHARGE, tx.type)
        assertEquals(Status.PENDING, tx.status)
        assertEquals(paymentResponse.transactionId, tx.gatewayTransactionId)
        assertNull(tx.financialTransactionId)
        assertNull(tx.supplierErrorCode)
        assertEquals(request.description, tx.description)
        assertNull(tx.errorCode)

        assertEquals(5000.0, balanceDao.findByAccountId(USER_ID).get().amount)
        assertEquals(100000.0, balanceDao.findByAccountId(RECIPIENT_ID).get().amount)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_PENDING.urn), payload.capture())
        assertEquals(TransactionType.CHARGE.name, payload.firstValue.type)
        assertEquals(tx.id, payload.firstValue.transactionId)
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateChargeController.sql"])
    fun notEnoughFunds() {
        // GIVEN
        val e = PaymentException(
            error = Error(
                code = ErrorCode.NOT_ENOUGH_FUNDS,
                transactionId = "111",
                supplierErrorCode = "xxxx"
            )
        )
        doThrow(e).whenever(gateway).createPayment(any())

        // WHEN
        val request = createRequest(amount = 50000000.0)
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateChargeResponse::class.java)
        }

        // THEN
        assertEquals(409, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.TRANSACTION_FAILED.urn, response.error.code)
        assertEquals(ErrorCode.NOT_ENOUGH_FUNDS.name, response.error.downstreamCode)

        val txId = response.error.data?.get("transaction-id").toString()
        val tx = txDao.findById(txId).get()
        val fees = 5000000.0
        assertEquals(USER_ID, tx.accountId)
        assertEquals(request.currency, tx.currency)
        assertEquals(request.amount, tx.amount)
        assertEquals(fees, tx.fees)
        assertEquals(request.amount - tx.fees, tx.net)
        assertEquals(request.paymentMethodToken, tx.paymentMethodToken)
        assertEquals(PaymentMethodProvider.MTN, tx.paymentMethodProvider)
        assertEquals(TransactionType.CHARGE, tx.type)
        assertEquals(Status.FAILED, tx.status)
        assertNull(tx.financialTransactionId)
        assertEquals(request.description, tx.description)
        assertEquals(e.error.supplierErrorCode, tx.supplierErrorCode)
        assertEquals(ErrorCode.NOT_ENOUGH_FUNDS.name, tx.errorCode)
        assertEquals(e.error.transactionId, tx.gatewayTransactionId)

        assertEquals(5000.0, balanceDao.findByAccountId(USER_ID).get().amount)
        assertEquals(100000.0, balanceDao.findByAccountId(RECIPIENT_ID).get().amount)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_FAILED.urn), payload.capture())
        assertEquals(TransactionType.CHARGE.name, payload.firstValue.type)
        assertEquals(tx.id, payload.firstValue.transactionId)
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateChargeController.sql"])
    fun paymentDeclined() {
        // GIVEN
        val e = PaymentException(
            error = Error(
                code = ErrorCode.DECLINED,
                transactionId = "111",
                supplierErrorCode = "xxxx"
            )
        )
        doThrow(e).whenever(gateway).createPayment(any())

        // WHEN
        val request = createRequest()
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateChargeResponse::class.java)
        }

        // THEN
        assertEquals(409, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.TRANSACTION_FAILED.urn, response.error.code)
        assertEquals(ErrorCode.DECLINED.name, response.error.downstreamCode)

        val txId = response.error.data?.get("transaction-id").toString()
        val tx = txDao.findById(txId).get()
        val fees = 5000.0
        assertEquals(USER_ID, tx.accountId)
        assertEquals(request.currency, tx.currency)
        assertEquals(request.amount, tx.amount)
        assertEquals(fees, tx.fees)
        assertEquals(request.amount - tx.fees, tx.net)
        assertEquals(request.paymentMethodToken, tx.paymentMethodToken)
        assertEquals(PaymentMethodProvider.MTN, tx.paymentMethodProvider)
        assertEquals(TransactionType.CHARGE, tx.type)
        assertEquals(Status.FAILED, tx.status)
        assertNull(tx.financialTransactionId)
        assertEquals(request.description, tx.description)
        assertEquals(e.error.supplierErrorCode, tx.supplierErrorCode)
        assertEquals(ErrorCode.DECLINED.name, tx.errorCode)
        assertEquals(e.error.transactionId, tx.gatewayTransactionId)

        assertEquals(5000.0, balanceDao.findByAccountId(USER_ID).get().amount)
        assertEquals(100000.0, balanceDao.findByAccountId(RECIPIENT_ID).get().amount)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_FAILED.urn), payload.capture())
        assertEquals(TransactionType.CHARGE.name, payload.firstValue.type)
        assertEquals(tx.id, payload.firstValue.transactionId)
    }

    @Test
    fun badCurrency() {
        // WHEN
        val request = createRequest(currency = "EUR")
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateChargeResponse::class.java)
        }

        // THEN
        assertEquals(400, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.CURRENCY_NOT_SUPPORTED.urn, response.error.code)
    }

    @Test
    fun recipientNotActive() {
        // GIVEN
        val account = AccountSummary(id = USER_ID, status = AccountStatus.ACTIVE.name)
        val recipient = AccountSummary(id = RECIPIENT_ID, status = AccountStatus.SUSPENDED.name)
        doReturn(SearchAccountResponse(listOf(account, recipient))).whenever(accountApi).searchAccount(any())

        // WHEN
        val request = createRequest()
        val e = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateChargeResponse::class.java)
        }

        // THEN
        assertEquals(403, e.rawStatusCode)

        val response = ObjectMapper().readValue(e.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.RECIPIENT_NOT_ACTIVE.urn, response.error.code)

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    fun recipientNotBusiness() {
        // GIVEN
        val accounts = listOf(
            AccountSummary(id = USER_ID, status = AccountStatus.ACTIVE.name, business = false),
            AccountSummary(id = RECIPIENT_ID, status = AccountStatus.ACTIVE.name, business = false)
        )
        doReturn(SearchAccountResponse(accounts)).whenever(accountApi).searchAccount(any())

        // WHEN
        val request = createRequest()
        val e = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateChargeResponse::class.java)
        }

        // THEN
        assertEquals(403, e.rawStatusCode)

        val response = ObjectMapper().readValue(e.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.RESTRICTED_TO_BUSINESS_ACCOUNT.urn, response.error.code)

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    public fun invalidRecipient() {
        // WHEN
        val request = createRequest(recipientId = 99999)
        val e = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateCashinResponse::class.java)
        }

        // THEN
        assertEquals(409, e.rawStatusCode)

        val response = ObjectMapper().readValue(e.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.RECIPIENT_NOT_FOUND.urn, response.error.code)

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    fun userNotActive() {
        // GIVEN
        val account = AccountSummary(id = USER_ID, status = AccountStatus.SUSPENDED.name)
        val recipient = AccountSummary(id = RECIPIENT_ID, status = AccountStatus.ACTIVE.name)
        doReturn(SearchAccountResponse(listOf(account, recipient))).whenever(accountApi).searchAccount(any())

        // WHEN
        val request = createRequest()
        val e = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateChargeResponse::class.java)
        }

        // THEN
        assertEquals(403, e.rawStatusCode)

        val response = ObjectMapper().readValue(e.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.USER_NOT_ACTIVE.urn, response.error.code)

        verify(eventStream, never()).publish(any(), any())
    }

    private fun createRequest(amount: Double = 50000.0, currency: String = "XAF", recipientId: Long = RECIPIENT_ID) =
        CreateChargeRequest(
            paymentMethodToken = "11111",
            recipientId = recipientId,
            amount = amount,
            currency = currency,
            description = "Hello world"
        )
}
