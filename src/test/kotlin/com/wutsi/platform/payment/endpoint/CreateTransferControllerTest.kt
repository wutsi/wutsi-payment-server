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
import com.wutsi.platform.payment.dto.CreateCashoutResponse
import com.wutsi.platform.payment.dto.CreateTransferRequest
import com.wutsi.platform.payment.dto.CreateTransferResponse
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
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.jdbc.Sql
import org.springframework.web.client.HttpClientErrorException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/CreateTransferController.sql"])
class CreateTransferControllerTest : AbstractSecuredController() {
    @LocalServerPort
    val port: Int = 0
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

        url = "http://localhost:$port/v1/transactions/transfers"

        // GIVEN
        val account = AccountSummary(id = USER_ID, status = "ACTIVE")
        val recipient = AccountSummary(id = 200, status = "ACTIVE")
        doReturn(SearchAccountResponse(listOf(account, recipient))).whenever(accountApi).searchAccount(any())
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateTransferController.sql"])
    public fun success() {
        // WHEN
        val request = createRequest()
        val response = rest.postForEntity(url, request, CreateTransferResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        assertEquals(Status.SUCCESSFUL.name, response.body!!.status)

        val fees = 0.0
        val tx = txDao.findById(response.body!!.id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.accountId)
        assertEquals(request.currency, tx.currency)
        assertEquals(request.amount + fees, tx.amount)
        assertEquals(fees, tx.fees)
        assertEquals(request.amount, tx.net)
        assertNull(tx.paymentMethodToken)
        assertNull(tx.paymentMethodProvider)
        assertEquals(TransactionType.TRANSFER, tx.type)
        assertEquals(Status.SUCCESSFUL, tx.status)
        assertEquals(request.description, tx.description)
        assertNull(tx.gatewayTransactionId)
        assertNull(tx.financialTransactionId)
        assertNull(tx.errorCode)
        assertNull(tx.supplierErrorCode)
        assertFalse(tx.business)
        assertNull(tx.orderId)
        assertEquals(request.idempotencyKey, tx.idempotencyKey)
        assertFalse(tx.applyFeesToSender)

        val balance = balanceDao.findByAccountId(USER_ID).get()
        assertEquals(100000 - tx.amount, balance.amount)
        assertEquals(request.currency, balance.currency)

        val balance2 = balanceDao.findByAccountId(request.recipientId).get()
        assertEquals(200000 + tx.net, balance2.amount)
        assertEquals(request.currency, balance2.currency)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_SUCCESSFUL.urn), payload.capture())
        assertEquals(TransactionType.TRANSFER.name, payload.firstValue.type)
        assertEquals(tx.id, payload.firstValue.transactionId)
        assertEquals(tx.orderId, payload.firstValue.orderId)
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateTransferController.sql"])
    fun successIdempotent() {
        // WHEN
        val request = createRequest(idempotencyKey = "i-100")
        val response = rest.postForEntity(url, request, CreateTransferResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        assertEquals(Status.SUCCESSFUL.name, response.body!!.status)
        assertEquals("100", response.body!!.id)

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateTransferController.sql"])
    public fun transferToBusiness() {
        // GIVEN
        val account = AccountSummary(id = USER_ID, status = "ACTIVE")
        val recipient = AccountSummary(id = 200, status = "ACTIVE", business = true)
        doReturn(SearchAccountResponse(listOf(account, recipient))).whenever(accountApi).searchAccount(any())

        // WHEN
        val request = createRequest()
        val response = rest.postForEntity(url, request, CreateTransferResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        assertEquals(Status.SUCCESSFUL.name, response.body!!.status)

        val fees = 0.0
        val tx = txDao.findById(response.body!!.id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.accountId)
        assertEquals(request.currency, tx.currency)
        assertEquals(request.amount + fees, tx.amount)
        assertEquals(fees, tx.fees)
        assertEquals(request.amount, tx.net)
        assertNull(tx.paymentMethodToken)
        assertNull(tx.paymentMethodProvider)
        assertEquals(TransactionType.TRANSFER, tx.type)
        assertEquals(Status.SUCCESSFUL, tx.status)
        assertEquals(request.description, tx.description)
        assertNull(tx.gatewayTransactionId)
        assertNull(tx.financialTransactionId)
        assertNull(tx.errorCode)
        assertNull(tx.supplierErrorCode)
        assertTrue(tx.business)
        assertNull(tx.orderId)
        assertEquals(request.idempotencyKey, tx.idempotencyKey)
        assertFalse(tx.applyFeesToSender)

        val balance = balanceDao.findByAccountId(USER_ID).get()
        assertEquals(100000 - tx.amount, balance.amount)
        assertEquals(request.currency, balance.currency)

        val balance2 = balanceDao.findByAccountId(request.recipientId).get()
        assertEquals(200000 + tx.net, balance2.amount)
        assertEquals(request.currency, balance2.currency)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_SUCCESSFUL.urn), payload.capture())
        assertEquals(TransactionType.TRANSFER.name, payload.firstValue.type)
        assertEquals(tx.id, payload.firstValue.transactionId)
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateTransferController.sql"])
    public fun notEnoughFunds() {
        // WHEN
        val request = createRequest(amount = 50000000.0)
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateTransferResponse::class.java)
        }

        // THEN
        assertEquals(409, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.TRANSACTION_FAILED.urn, response.error.code)
        assertEquals(ErrorCode.NOT_ENOUGH_FUNDS.name, response.error.downstreamCode)

        val id = response.error.data?.get("transaction-id")
        assertNull(id)

        val balance = balanceDao.findByAccountId(USER_ID).get()
        assertEquals(100000.0, balance.amount)
        assertEquals(request.currency, balance.currency)

        val balance2 = balanceDao.findByAccountId(request.recipientId).get()
        assertEquals(200000.0, balance2.amount)
        assertEquals(request.currency, balance2.currency)

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateTransferController.sql"])
    fun errorIdempotency() {
        // WHEN
        val request = createRequest(idempotencyKey = "i-300")
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateCashoutResponse::class.java)
        }

        // THEN
        assertEquals(409, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.TRANSACTION_FAILED.urn, response.error.code)
        assertEquals(ErrorCode.NOT_ENOUGH_FUNDS.name, response.error.downstreamCode)

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    public fun recipientNotActive() {
        // GIVEN
        val account = AccountSummary(id = USER_ID, status = "ACTIVE")
        val recipient = AccountSummary(id = 200, status = "SUSPENDED")
        doReturn(SearchAccountResponse(listOf(account, recipient))).whenever(accountApi).searchAccount(any())

        // WHEN
        val request = createRequest()
        val e = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateCashinResponse::class.java)
        }

        // THEN
        assertEquals(403, e.rawStatusCode)

        val response = ObjectMapper().readValue(e.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.RECIPIENT_NOT_ACTIVE.urn, response.error.code)

        assertNull(response.error.data?.get("id"))

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    public fun userNotActive() {
        // GIVEN
        val account = AccountSummary(id = USER_ID, status = "SUSPENDED")
        val recipient = AccountSummary(id = 200, status = "ACTIVE")
        doReturn(SearchAccountResponse(listOf(account, recipient))).whenever(accountApi).searchAccount(any())

        // WHEN
        val request = createRequest()
        val e = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateCashinResponse::class.java)
        }

        // THEN
        assertEquals(403, e.rawStatusCode)

        val response = ObjectMapper().readValue(e.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.USER_NOT_ACTIVE.urn, response.error.code)

        assertNull(response.error.data?.get("id"))

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    public fun selfTransaction() {
        // WHEN
        val request = createRequest(recipientId = USER_ID)
        val e = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateCashinResponse::class.java)
        }

        // THEN
        assertEquals(403, e.rawStatusCode)

        val response = ObjectMapper().readValue(e.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.SELF_TRANSACTION_ERROR.urn, response.error.code)

        assertNull(response.error.data?.get("id"))

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    public fun invalidRecipient() {
        // WHEN
        val request = createRequest(recipientId = 444L)
        val e = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateCashinResponse::class.java)
        }

        // THEN
        assertEquals(409, e.rawStatusCode)

        val response = ObjectMapper().readValue(e.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.RECIPIENT_NOT_FOUND.urn, response.error.code)

        assertNull(response.error.data?.get("id"))

        verify(eventStream, never()).publish(any(), any())
    }

    private fun createRequest(recipientId: Long = 200, amount: Double = 50000.0, idempotencyKey: String? = null) =
        CreateTransferRequest(
            amount = amount,
            currency = "XAF",
            recipientId = recipientId,
            description = "Yo man",
            idempotencyKey = idempotencyKey ?: UUID.randomUUID().toString()
        )
}
