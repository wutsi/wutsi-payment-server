package com.wutsi.platform.payment.endpoint

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.account.dto.Account
import com.wutsi.platform.account.dto.GetAccountResponse
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
import kotlin.test.assertNull

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

        url = "http://localhost:$port/v1/payments"
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

        val tx = txDao.findById(response.body!!.id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.accountId)
        assertEquals("XAF", tx.currency)
        assertEquals(50000.0, tx.amount)
        assertEquals(0.0, tx.fees)
        assertEquals(50000.0, tx.net)
        assertNull(tx.paymentMethodToken)
        assertNull(tx.paymentMethodProvider)
        assertEquals(TransactionType.PAYMENT, tx.type)
        assertEquals(Status.SUCCESSFUL, tx.status)
        assertEquals("This is description", tx.description)
        assertNull(tx.gatewayTransactionId)
        assertNull(tx.financialTransactionId)
        assertNull(tx.errorCode)
        assertNull(tx.supplierErrorCode)

        val balance = balanceDao.findByAccountId(USER_ID).get()
        assertEquals(50000.0, balance.amount)
        assertEquals("XAF", balance.currency)

        val balance2 = balanceDao.findByAccountId(200).get()
        assertEquals(250000.0, balance2.amount)
        assertEquals("XAF", balance2.currency)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_SUCCESSFULL.urn), payload.capture())
        assertEquals(USER_ID, payload.firstValue.accountId)
        assertEquals(TransactionType.PAYMENT.name, payload.firstValue.type)
        assertEquals(200L, payload.firstValue.recipientId)
        assertEquals(tx.id, payload.firstValue.transactionId)
        assertEquals(tx.tenantId, payload.firstValue.tenantId)
        assertEquals(tx.amount, payload.firstValue.amount)
        assertEquals(tx.currency, payload.firstValue.currency)
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

        val id = response.error.data?.get("id").toString()
        val tx = txDao.findById(id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.accountId)
        assertEquals("XAF", tx.currency)
        assertEquals(5000000.0, tx.amount)
        assertEquals(0.0, tx.fees)
        assertEquals(5000000.0, tx.net)
        assertNull(tx.paymentMethodToken)
        assertNull(tx.paymentMethodProvider)
        assertEquals(TransactionType.PAYMENT, tx.type)
        assertEquals(Status.FAILED, tx.status)
        assertEquals("This is description", tx.description)
        assertNull(tx.gatewayTransactionId)
        assertNull(tx.financialTransactionId)
        assertEquals(ErrorCode.NOT_ENOUGH_FUNDS.name, tx.errorCode)
        assertNull(tx.supplierErrorCode)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_FAILED.urn), payload.capture())
        assertEquals(USER_ID, payload.firstValue.accountId)
        assertEquals(TransactionType.PAYMENT.name, payload.firstValue.type)
        assertEquals(200L, payload.firstValue.recipientId)
        assertEquals(tx.id, payload.firstValue.transactionId)
        assertEquals(tx.tenantId, payload.firstValue.tenantId)
        assertEquals(tx.amount, payload.firstValue.amount)
        assertEquals(tx.currency, payload.firstValue.currency)
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

        val id = response.error.data?.get("id").toString()
        val tx = txDao.findById(id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.accountId)
        assertEquals("XAF", tx.currency)
        assertEquals(1000.0, tx.amount)
        assertEquals(0.0, tx.fees)
        assertEquals(1000.0, tx.net)
        assertNull(tx.paymentMethodToken)
        assertNull(tx.paymentMethodProvider)
        assertEquals(TransactionType.PAYMENT, tx.type)
        assertEquals(Status.FAILED, tx.status)
        assertEquals("This is description", tx.description)
        assertNull(tx.gatewayTransactionId)
        assertNull(tx.financialTransactionId)
        assertEquals(ErrorCode.EXPIRED.name, tx.errorCode)
        assertNull(tx.supplierErrorCode)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_FAILED.urn), payload.capture())
        assertEquals(USER_ID, payload.firstValue.accountId)
        assertEquals(TransactionType.PAYMENT.name, payload.firstValue.type)
        assertEquals(200L, payload.firstValue.recipientId)
        assertEquals(tx.id, payload.firstValue.transactionId)
        assertEquals(tx.tenantId, payload.firstValue.tenantId)
        assertEquals(tx.amount, payload.firstValue.amount)
        assertEquals(tx.currency, payload.firstValue.currency)
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreatePaymentController.sql"])
    public fun merchantNotActive() {
        // GIVEN
        val account = Account(status = "SUSPENDED")
        doReturn(GetAccountResponse(account)).whenever(accountApi).getAccount(200L)

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
        val account = Account(status = "SUSPENDED")
        doReturn(GetAccountResponse(account)).whenever(accountApi).getAccount(USER_ID)

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
}
