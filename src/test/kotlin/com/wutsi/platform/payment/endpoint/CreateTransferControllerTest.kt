package com.wutsi.platform.payment.endpoint

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.account.dto.Account
import com.wutsi.platform.account.dto.GetAccountResponse
import com.wutsi.platform.core.error.ErrorResponse
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.core.ErrorCode
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.AccountRepository
import com.wutsi.platform.payment.dao.RecordRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.CreateCashinResponse
import com.wutsi.platform.payment.dto.CreateTransferRequest
import com.wutsi.platform.payment.dto.CreateTransferResponse
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.event.TransactionEventPayload
import com.wutsi.platform.payment.util.ErrorURN
import feign.FeignException
import feign.Request
import feign.Request.HttpMethod.POST
import feign.RequestTemplate
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.jdbc.Sql
import org.springframework.web.client.HttpClientErrorException
import java.nio.charset.Charset
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/CreateTransferController.sql"])
public class CreateTransferControllerTest : AbstractSecuredController() {
    @LocalServerPort
    public val port: Int = 0
    private lateinit var url: String

    @Autowired
    private lateinit var txDao: TransactionRepository

    @Autowired
    private lateinit var recordDao: RecordRepository

    @Autowired
    private lateinit var accountDao: AccountRepository

    @MockBean
    private lateinit var eventStream: EventStream

    @BeforeEach
    override fun setUp() {
        super.setUp()

        url = "http://localhost:$port/v1/transactions/transfers"
    }

    @Test
    public fun success() {
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

        val tx = txDao.findById(response.body.id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.userId)
        assertEquals(request.currency, tx.currency)
        assertEquals(request.amount, tx.amount)
        assertNull(tx.paymentMethodToken)
        assertNull(tx.paymentMethodProvider)
        assertEquals(TransactionType.TRANSFER, tx.type)
        assertEquals(Status.SUCCESSFUL, tx.status)
        assertEquals(request.description, tx.description)
        assertEquals(1L, tx.fromAccount?.id)
        assertEquals(200L, tx.toAccount?.id)
        assertNull(tx.gatewayTransactionId)
        assertNull(tx.financialTransactionId)
        assertNull(tx.errorCode)
        assertNull(tx.supplierErrorCode)

        val records = recordDao.findByTransaction(tx)
        assertEquals(2, records.size)

        val fromRecord = records[0]
        assertEquals(request.amount, fromRecord.debit)
        assertEquals(0.0, fromRecord.credit)
        assertEquals("XAF", fromRecord.currency)

        val fromAccount = accountDao.findById(fromRecord.account.id).get()
        assertEquals(100000 - request.amount, fromAccount.balance)

        val toRecord = records[1]
        assertEquals(0.0, toRecord.debit)
        assertEquals(request.amount, toRecord.credit)
        assertEquals("XAF", toRecord.currency)

        val toAccount = accountDao.findById(toRecord.account.id).get()
        assertEquals(10000 + request.amount, toAccount.balance)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_SUCCESSFULL.urn), payload.capture())
        assertEquals(USER_ID, payload.firstValue.senderId)
        assertEquals(TransactionType.TRANSFER.name, payload.firstValue.type)
        assertEquals(request.recipientId, payload.firstValue.recipientId)
        assertEquals(tx.id, payload.firstValue.transactionId)
        assertEquals(tx.tenantId, payload.firstValue.tenantId)
        assertEquals(tx.amount, payload.firstValue.amount)
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

        val id = response.error.data?.get("id").toString()
        val tx = txDao.findById(id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.userId)
        assertEquals(request.currency, tx.currency)
        assertEquals(request.amount, tx.amount)
        assertNull(tx.paymentMethodToken)
        assertNull(tx.paymentMethodProvider)
        assertEquals(TransactionType.TRANSFER, tx.type)
        assertEquals(Status.FAILED, tx.status)
        assertEquals(request.description, tx.description)
        assertEquals(1L, tx.fromAccount?.id)
        assertEquals(200L, tx.toAccount?.id)
        assertNull(tx.gatewayTransactionId)
        assertNull(tx.financialTransactionId)
        assertEquals(ErrorCode.NOT_ENOUGH_FUNDS.name, tx.errorCode)
        assertNull(tx.supplierErrorCode)

        val records = recordDao.findByTransaction(tx)
        assertEquals(0, records.size)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_FAILED.urn), payload.capture())
        assertEquals(USER_ID, payload.firstValue.senderId)
        assertEquals(TransactionType.TRANSFER.name, payload.firstValue.type)
        assertEquals(request.recipientId, payload.firstValue.recipientId)
        assertEquals(tx.id, payload.firstValue.transactionId)
        assertEquals(tx.tenantId, payload.firstValue.tenantId)
        assertEquals(tx.amount, payload.firstValue.amount)
        assertEquals(tx.currency, payload.firstValue.currency)
    }

    @Test
    public fun recipientNotFound() {
        // GIVEN
        val ex = FeignException.NotFound(
            "",
            Request.create(POST, "", emptyMap(), "".toByteArray(), Charset.defaultCharset(), RequestTemplate()),
            "".toByteArray(),
            emptyMap()
        )
        doThrow(ex).whenever(accountApi).getAccount(200L)

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
        assertEquals(409, e.rawStatusCode)

        val response = ObjectMapper().readValue(e.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.TRANSACTION_FAILED.urn, response.error.code)
        assertEquals(ErrorCode.PAYEE_NOT_ALLOWED_TO_RECEIVE.name, response.error.downstreamCode)

        val id = response.error.data?.get("id").toString()
        val tx = txDao.findById(id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.userId)
        assertEquals(request.currency, tx.currency)
        assertEquals(request.amount, tx.amount)
        assertNull(tx.paymentMethodToken)
        assertNull(tx.paymentMethodProvider)
        assertEquals(TransactionType.TRANSFER, tx.type)
        assertEquals(Status.FAILED, tx.status)
        assertEquals(request.description, tx.description)
        assertEquals(1L, tx.fromAccount?.id)
        assertEquals(200L, tx.toAccount?.id)
        assertNull(tx.gatewayTransactionId)
        assertNull(tx.financialTransactionId)
        assertEquals(ErrorCode.PAYEE_NOT_ALLOWED_TO_RECEIVE.name, tx.errorCode)
        assertNull(tx.supplierErrorCode)

        val records = recordDao.findByTransaction(tx)
        assertEquals(0, records.size)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_FAILED.urn), payload.capture())
        assertEquals(USER_ID, payload.firstValue.senderId)
        assertEquals(TransactionType.TRANSFER.name, payload.firstValue.type)
        assertEquals(request.recipientId, payload.firstValue.recipientId)
        assertEquals(tx.id, payload.firstValue.transactionId)
        assertEquals(tx.tenantId, payload.firstValue.tenantId)
        assertEquals(tx.amount, payload.firstValue.amount)
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
        assertEquals(409, e.rawStatusCode)

        val response = ObjectMapper().readValue(e.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.TRANSACTION_FAILED.urn, response.error.code)
        assertEquals(ErrorCode.PAYEE_NOT_ALLOWED_TO_RECEIVE.name, response.error.downstreamCode)

        val id = response.error.data?.get("id").toString()
        val tx = txDao.findById(id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.userId)
        assertEquals(request.currency, tx.currency)
        assertEquals(request.amount, tx.amount)
        assertNull(tx.paymentMethodToken)
        assertNull(tx.paymentMethodProvider)
        assertEquals(TransactionType.TRANSFER, tx.type)
        assertEquals(Status.FAILED, tx.status)
        assertEquals(request.description, tx.description)
        assertEquals(1L, tx.fromAccount?.id)
        assertEquals(200L, tx.toAccount?.id)
        assertNull(tx.gatewayTransactionId)
        assertNull(tx.financialTransactionId)
        assertEquals(ErrorCode.PAYEE_NOT_ALLOWED_TO_RECEIVE.name, tx.errorCode)
        assertNull(tx.supplierErrorCode)

        val records = recordDao.findByTransaction(tx)
        assertEquals(0, records.size)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_FAILED.urn), payload.capture())
        assertEquals(USER_ID, payload.firstValue.senderId)
        assertEquals(TransactionType.TRANSFER.name, payload.firstValue.type)
        assertEquals(request.recipientId, payload.firstValue.recipientId)
        assertEquals(tx.id, payload.firstValue.transactionId)
        assertEquals(tx.tenantId, payload.firstValue.tenantId)
        assertEquals(tx.amount, payload.firstValue.amount)
        assertEquals(tx.currency, payload.firstValue.currency)
    }
}
