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
import com.wutsi.platform.account.dto.AccountSummary
import com.wutsi.platform.account.dto.GetAccountResponse
import com.wutsi.platform.account.dto.SearchAccountResponse
import com.wutsi.platform.core.error.ErrorResponse
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.core.ErrorCode
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.BalanceRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.CreateCashinResponse
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
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.jdbc.Sql
import org.springframework.web.client.HttpClientErrorException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/CreateTransferController.sql"])
public class CreateTransferControllerTest : AbstractSecuredController() {
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

        url = "http://localhost:$port/v1/transactions/transfers"

        // GIVEN
        val account = AccountSummary(id = USER_ID)
        val recipient = AccountSummary(id = 200)
        doReturn(SearchAccountResponse(listOf(account, recipient))).whenever(accountApi).searchAccount(any())
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateTransferController.sql"])
    public fun transferToPerson() {
        // WHEN
        val request = CreateTransferRequest(
            amount = 50000.0,
            currency = "XAF",
            recipientId = 200,
            description = "Yo man"
        )
        val response = rest.postForEntity(url, request, CreateTransferResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        assertEquals(Status.SUCCESSFUL.name, response.body.status)

        val fees = 100.0
        val tx = txDao.findById(response.body.id).get()
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
        assertNull(tx.paymentRequestId)
        assertFalse(tx.business)
        assertFalse(tx.retail)

        val balance = balanceDao.findByAccountId(USER_ID).get()
        assertEquals(100000 - tx.amount, balance.amount)
        assertEquals(request.currency, balance.currency)

        val balance2 = balanceDao.findByAccountId(request.recipientId).get()
        assertEquals(200000 + tx.net, balance2.amount)
        assertEquals(request.currency, balance2.currency)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_SUCCESSFULL.urn), payload.capture())
        assertEquals(USER_ID, payload.firstValue.accountId)
        assertEquals(TransactionType.TRANSFER.name, payload.firstValue.type)
        assertEquals(request.recipientId, payload.firstValue.recipientId)
        assertEquals(tx.id, payload.firstValue.transactionId)
        assertEquals(tx.tenantId, payload.firstValue.tenantId)
        assertEquals(tx.amount, payload.firstValue.amount)
        assertEquals(tx.net, payload.firstValue.net)
        assertEquals(tx.currency, payload.firstValue.currency)
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateTransferController.sql"])
    public fun transferToBusiness() {
        // GIVEN
        val account = AccountSummary(id = USER_ID)
        val recipient = AccountSummary(id = 200, business = true, retail = false)
        doReturn(SearchAccountResponse(listOf(account, recipient))).whenever(accountApi).searchAccount(any())

        // WHEN
        val request = CreateTransferRequest(
            amount = 50000.0,
            currency = "XAF",
            recipientId = 200,
            description = "Yo man"
        )
        val response = rest.postForEntity(url, request, CreateTransferResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        assertEquals(Status.SUCCESSFUL.name, response.body.status)

        val fees = 1000.0
        val tx = txDao.findById(response.body.id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.accountId)
        assertEquals(request.currency, tx.currency)
        assertEquals(request.amount, tx.amount)
        assertEquals(fees, tx.fees)
        assertEquals(request.amount - fees, tx.net)
        assertNull(tx.paymentMethodToken)
        assertNull(tx.paymentMethodProvider)
        assertEquals(TransactionType.TRANSFER, tx.type)
        assertEquals(Status.SUCCESSFUL, tx.status)
        assertEquals(request.description, tx.description)
        assertNull(tx.gatewayTransactionId)
        assertNull(tx.financialTransactionId)
        assertNull(tx.errorCode)
        assertNull(tx.supplierErrorCode)
        assertNull(tx.paymentRequestId)
        assertTrue(tx.business)
        assertFalse(tx.retail)

        val balance = balanceDao.findByAccountId(USER_ID).get()
        assertEquals(100000 - tx.amount, balance.amount)
        assertEquals(request.currency, balance.currency)

        val balance2 = balanceDao.findByAccountId(request.recipientId).get()
        assertEquals(200000 + tx.net, balance2.amount)
        assertEquals(request.currency, balance2.currency)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_SUCCESSFULL.urn), payload.capture())
        assertEquals(USER_ID, payload.firstValue.accountId)
        assertEquals(TransactionType.TRANSFER.name, payload.firstValue.type)
        assertEquals(request.recipientId, payload.firstValue.recipientId)
        assertEquals(tx.id, payload.firstValue.transactionId)
        assertEquals(tx.tenantId, payload.firstValue.tenantId)
        assertEquals(tx.amount, payload.firstValue.amount)
        assertEquals(tx.net, payload.firstValue.net)
        assertEquals(tx.currency, payload.firstValue.currency)
    }

    @Test
    public fun notEnoughFunds() {
        // WHEN
        val request = CreateTransferRequest(
            amount = 50000000.0,
            currency = "XAF",
            recipientId = 200,
            description = "Yo man"
        )
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateCashinResponse::class.java)
        }

        // THEN
        assertEquals(409, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.TRANSACTION_FAILED.urn, response.error.code)
        assertEquals(ErrorCode.NOT_ENOUGH_FUNDS.name, response.error.downstreamCode)

        val fees = 100.0
        val id = response.error.data?.get("id").toString()
        val tx = txDao.findById(id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.accountId)
        assertEquals(request.currency, tx.currency)
        assertEquals(request.amount + fees, tx.amount)
        assertEquals(fees, tx.fees)
        assertEquals(request.amount, tx.net)
        assertNull(tx.paymentMethodToken)
        assertNull(tx.paymentMethodProvider)
        assertEquals(TransactionType.TRANSFER, tx.type)
        assertEquals(Status.FAILED, tx.status)
        assertEquals(request.description, tx.description)
        assertNull(tx.gatewayTransactionId)
        assertNull(tx.financialTransactionId)
        assertEquals(ErrorCode.NOT_ENOUGH_FUNDS.name, tx.errorCode)
        assertNull(tx.supplierErrorCode)
        assertNull(tx.paymentRequestId)
        assertFalse(tx.business)
        assertFalse(tx.retail)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_FAILED.urn), payload.capture())
        assertEquals(USER_ID, payload.firstValue.accountId)
        assertEquals(TransactionType.TRANSFER.name, payload.firstValue.type)
        assertEquals(request.recipientId, payload.firstValue.recipientId)
        assertEquals(tx.id, payload.firstValue.transactionId)
        assertEquals(tx.tenantId, payload.firstValue.tenantId)
        assertEquals(tx.amount, payload.firstValue.amount)
        assertEquals(tx.net, payload.firstValue.net)
        assertEquals(tx.currency, payload.firstValue.currency)
    }

    @Test
    public fun recipientNotActive() {
        // GIVEN
        val account = Account(status = "SUSPENDED")
        doReturn(GetAccountResponse(account)).whenever(accountApi).getAccount(200L)

        // WHEN
        val request = CreateTransferRequest(
            amount = 100.0,
            currency = "XAF",
            recipientId = 200,
            description = "Yo man"
        )
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
        val account = Account(status = "SUSPENDED")
        doReturn(GetAccountResponse(account)).whenever(accountApi).getAccount(USER_ID)

        // WHEN
        val request = CreateTransferRequest(
            amount = 100.0,
            currency = "XAF",
            recipientId = 200,
            description = "Yo man"
        )
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
        val request = CreateTransferRequest(
            amount = 100.0,
            currency = "XAF",
            recipientId = USER_ID,
            description = "Yo man"
        )
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
}
