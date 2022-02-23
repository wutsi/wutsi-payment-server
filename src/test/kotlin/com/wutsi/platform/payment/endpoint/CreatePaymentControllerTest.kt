package com.wutsi.platform.payment.endpoint

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.account.dto.AccountSummary
import com.wutsi.platform.account.dto.SearchAccountResponse
import com.wutsi.platform.core.error.ErrorResponse
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.core.ErrorCode
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.BalanceRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.CreateCashinResponse
import com.wutsi.platform.payment.dto.CreatePaymentRequest
import com.wutsi.platform.payment.dto.CreatePaymentResponse
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.error.ErrorURN
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.event.TransactionEventPayload
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.jdbc.Sql
import org.springframework.web.client.HttpClientErrorException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/CreatePaymentController.sql"])
public class CreatePaymentControllerTest : AbstractSecuredController() {
    @LocalServerPort
    public val port: Int = 0
    private lateinit var url: String

    @Autowired
    private lateinit var balanceDao: BalanceRepository

    @Autowired
    private lateinit var txDao: TransactionRepository

    @MockBean
    private lateinit var eventStream: EventStream

    @BeforeEach
    override fun setUp() {
        super.setUp()

        url = "http://localhost:$port/v1/transactions/payments"

        // GIVEN
        val account = AccountSummary(id = USER_ID, status = "ACTIVE")
        val recipient = AccountSummary(id = 200, business = true, status = "ACTIVE")
        doReturn(SearchAccountResponse(listOf(account, recipient))).whenever(accountApi).searchAccount(any())
    }

    @Test
    public fun success() {
        // WHEN
        val request = CreatePaymentRequest(
            requestId = "200"
        )
        val response = rest.postForEntity(url, request, CreatePaymentResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        assertEquals(Status.SUCCESSFUL.name, response.body!!.status)

        val fees = 2000.0
        val tx = txDao.findById(response.body!!.id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.accountId)
        assertEquals(200L, tx.recipientId)
        assertEquals("XAF", tx.currency)
        assertEquals(50000.0, tx.amount)
        assertEquals(fees, tx.fees)
        assertEquals(50000.0 - fees, tx.net)
        assertNull(tx.paymentMethodToken)
        assertNull(tx.paymentMethodProvider)
        assertEquals(TransactionType.PAYMENT, tx.type)
        assertEquals(Status.SUCCESSFUL, tx.status)
        assertEquals("This is description", tx.description)
        assertNull(tx.gatewayTransactionId)
        assertNull(tx.financialTransactionId)
        assertNull(tx.errorCode)
        assertNull(tx.supplierErrorCode)
        assertEquals(request.requestId, tx.paymentRequestId)
        assertTrue(tx.business)
        assertFalse(tx.retail)
        assertNull(tx.expires)
        assertFalse(tx.requiresApproval)
        assertNull(tx.approved)

        val balance = balanceDao.findByAccountId(USER_ID).get()
        assertEquals(50000.0, balance.amount)
        assertEquals("XAF", balance.currency)

        val balance2 = balanceDao.findByAccountId(200).get()
        assertEquals(250000.0 - fees, balance2.amount)
        assertEquals("XAF", balance2.currency)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_SUCCESSFUL.urn), payload.capture())
        assertEquals(TransactionType.PAYMENT.name, payload.firstValue.type)
        assertEquals(tx.id, payload.firstValue.transactionId)
    }

    @Test
    public fun notEnoughFunds() {
        // WHEN
        val request = CreatePaymentRequest(
            requestId = "201"
        )
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreatePaymentResponse::class.java)
        }

        // THEN
        assertEquals(409, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.TRANSACTION_FAILED.urn, response.error.code)
        assertEquals(ErrorCode.NOT_ENOUGH_FUNDS.name, response.error.downstreamCode)

        val fees = 200000.0
        val id = response.error.data?.get("transaction-id").toString()
        val tx = txDao.findById(id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.accountId)
        assertEquals(200L, tx.recipientId)
        assertEquals("XAF", tx.currency)
        assertEquals(5000000.0, tx.amount)
        assertEquals(fees, tx.fees)
        assertEquals(5000000.0 - fees, tx.net)
        assertNull(tx.paymentMethodToken)
        assertNull(tx.paymentMethodProvider)
        assertEquals(TransactionType.PAYMENT, tx.type)
        assertEquals(Status.FAILED, tx.status)
        assertEquals("This is description", tx.description)
        assertNull(tx.gatewayTransactionId)
        assertNull(tx.financialTransactionId)
        assertEquals(ErrorCode.NOT_ENOUGH_FUNDS.name, tx.errorCode)
        assertNull(tx.supplierErrorCode)
        assertEquals(request.requestId, tx.paymentRequestId)
        assertTrue(tx.business)
        assertFalse(tx.retail)
        assertNull(tx.expires)
        assertFalse(tx.requiresApproval)
        assertNull(tx.approved)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_FAILED.urn), payload.capture())
        assertEquals(TransactionType.PAYMENT.name, payload.firstValue.type)
        assertEquals(tx.id, payload.firstValue.transactionId)
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreatePaymentController.sql"])
    public fun expired() {
        // WHEN
        val request = CreatePaymentRequest(
            requestId = "202"
        )
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreatePaymentResponse::class.java)
        }

        // THEN
        assertEquals(409, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.TRANSACTION_FAILED.urn, response.error.code)
        assertEquals(ErrorCode.EXPIRED.name, response.error.downstreamCode)

        val fees = 40.0
        val id = response.error.data?.get("transaction-id").toString()
        val tx = txDao.findById(id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.accountId)
        assertEquals(200L, tx.recipientId)
        assertEquals("XAF", tx.currency)
        assertEquals(1000.0, tx.amount)
        assertEquals(fees, tx.fees)
        assertEquals(1000.0 - fees, tx.net)
        assertNull(tx.paymentMethodToken)
        assertNull(tx.paymentMethodProvider)
        assertEquals(TransactionType.PAYMENT, tx.type)
        assertEquals(Status.FAILED, tx.status)
        assertEquals("This is description", tx.description)
        assertNull(tx.gatewayTransactionId)
        assertNull(tx.financialTransactionId)
        assertEquals(ErrorCode.EXPIRED.name, tx.errorCode)
        assertNull(tx.supplierErrorCode)
        assertEquals(request.requestId, tx.paymentRequestId)
        assertTrue(tx.business)
        assertFalse(tx.retail)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_FAILED.urn), payload.capture())
        assertEquals(TransactionType.PAYMENT.name, payload.firstValue.type)
        assertEquals(tx.id, payload.firstValue.transactionId)
    }

    @Test
    public fun merchantNotActive() {
        // GIVEN
        val account = AccountSummary(id = USER_ID, status = "ACTIVE")
        val recipient = AccountSummary(id = 200, business = true, status = "SUSPENDED")
        doReturn(SearchAccountResponse(listOf(account, recipient))).whenever(accountApi).searchAccount(any())

        // WHEN
        val request = CreatePaymentRequest(
            requestId = "200"
        )
        val e = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateCashinResponse::class.java)
        }

        // THEN
        assertEquals(403, e.rawStatusCode)

        val response = ObjectMapper().readValue(e.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.RECIPIENT_NOT_ACTIVE.urn, response.error.code)

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreatePaymentController.sql"])
    public fun customerNotActive() {
        // GIVEN
        val account = AccountSummary(id = USER_ID, status = "SUSPENDED")
        val recipient = AccountSummary(id = 200, business = true, status = "ACTIVE")
        doReturn(SearchAccountResponse(listOf(account, recipient))).whenever(accountApi).searchAccount(any())

        // WHEN
        val request = CreatePaymentRequest(
            requestId = "200"
        )
        val e = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateCashinResponse::class.java)
        }

        // THEN
        assertEquals(403, e.rawStatusCode)

        val response = ObjectMapper().readValue(e.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.USER_NOT_ACTIVE.urn, response.error.code)

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    public fun invalidRequest() {
        // WHEN
        val request = CreatePaymentRequest(
            requestId = "9999999"
        )
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreatePaymentResponse::class.java)
        }

        // THEN
        assertEquals(404, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.PAYMENT_REQUEST_NOT_FOUND.urn, response.error.code)

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    public fun invalidTenant() {
        // WHEN
        val request = CreatePaymentRequest(
            requestId = "777"
        )
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreatePaymentResponse::class.java)
        }

        // THEN
        assertEquals(403, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.ILLEGAL_TENANT_ACCESS.urn, response.error.code)

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    public fun selfTransaction() {
        // WHEN
        val request = CreatePaymentRequest(
            requestId = "203"
        )
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreatePaymentResponse::class.java)
        }

        // THEN
        assertEquals(403, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.SELF_TRANSACTION_ERROR.urn, response.error.code)

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    public fun restrictedToBusinessAccount() {
        // GIVEN
        val account = AccountSummary(id = USER_ID, status = "ACTIVE")
        val recipient = AccountSummary(id = 200, business = false, status = "ACTIVE")
        doReturn(SearchAccountResponse(listOf(account, recipient))).whenever(accountApi).searchAccount(any())

        // WHEN
        val request = CreatePaymentRequest(
            requestId = "200"
        )
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreatePaymentResponse::class.java)
        }

        // THEN
        assertEquals(403, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.RESTRICTED_TO_BUSINESS_ACCOUNT.urn, response.error.code)

        verify(eventStream, never()).publish(any(), any())
    }
}
