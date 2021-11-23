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
import com.wutsi.platform.core.error.ErrorResponse
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.core.Error
import com.wutsi.platform.payment.core.ErrorCode
import com.wutsi.platform.payment.core.ErrorCode.NOT_ENOUGH_FUNDS
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.AccountRepository
import com.wutsi.platform.payment.dao.RecordRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.CreateCashinRequest
import com.wutsi.platform.payment.dto.CreateCashinResponse
import com.wutsi.platform.payment.dto.CreateCashoutRequest
import com.wutsi.platform.payment.dto.CreateCashoutResponse
import com.wutsi.platform.payment.entity.AccountType
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.event.TransactionEventPayload
import com.wutsi.platform.payment.model.CreateTransferResponse
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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/CreateCashoutController.sql"])
public class CreateCashoutControllerTest : AbstractSecuredController() {
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

        url = "http://localhost:$port/v1/transactions/cashouts"
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateCashoutController.sql"])
    fun success() {
        // GIVEN
        val paymentResponse = CreateTransferResponse("111", "222", Status.SUCCESSFUL)
        doReturn(paymentResponse).whenever(mtnGateway).createTransfer(any())

        // WHEN
        val request = CreateCashoutRequest(
            paymentMethodToken = "11111",
            amount = 50000.0,
            currency = "XAF"
        )
        val response = rest.postForEntity(url, request, CreateCashoutResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        assertEquals(Status.SUCCESSFUL.name, response.body.status)

        val tx = txDao.findById(response.body.id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.userId)
        assertEquals(request.currency, tx.currency)
        assertEquals(request.amount, tx.amount)
        assertEquals(request.paymentMethodToken, tx.paymentMethodToken)
        assertEquals(PaymentMethodProvider.MTN, tx.paymentMethodProvider)
        assertEquals(TransactionType.CASHOUT, tx.type)
        assertEquals(Status.SUCCESSFUL, tx.status)
        assertEquals(paymentResponse.transactionId, tx.gatewayTransactionId)
        assertEquals(paymentResponse.financialTransactionId, tx.financialTransactionId)
        assertNull(tx.supplierErrorCode)
        assertNull(tx.description)
        assertNull(tx.errorCode)

        val records = recordDao.findByTransaction(tx)
        assertEquals(2, records.size)

        val userRecord = records[0]
        assertEquals(0.0, userRecord.credit)
        assertEquals(request.amount, userRecord.debit)
        assertEquals("XAF", userRecord.currency)

        val userAccount = accountDao.findById(userRecord.account.id).get()
        assertEquals(100000.0 - request.amount, userAccount.balance)
        assertEquals(request.currency, userAccount.currency)
        assertEquals(AccountType.LIABILITY, userAccount.type)
        assertEquals(TENANT_ID, userAccount.tenantId)

        val gatewayRecord = records[1]
        assertEquals(request.amount, gatewayRecord.credit)
        assertEquals(0.0, gatewayRecord.debit)
        assertEquals(request.currency, gatewayRecord.currency)

        val gatewayAccount = accountDao.findById(gatewayRecord.account.id).get()
        assertEquals(500000.0 - request.amount, gatewayAccount.balance)
        assertEquals(request.currency, gatewayAccount.currency)
        assertEquals(AccountType.REVENUE, gatewayAccount.type)
        assertEquals(TENANT_ID, gatewayAccount.tenantId)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_SUCCESSFULL.urn), payload.capture())
        assertEquals(-1, payload.firstValue.userId)
        assertEquals(TransactionType.CASHOUT.name, payload.firstValue.type)
        assertEquals(-1, payload.firstValue.recipientId)
        assertEquals(tx.id, payload.firstValue.transactionId)
        assertEquals(tx.tenantId, payload.firstValue.tenantId)
        assertEquals(tx.amount, payload.firstValue.amount)
        assertEquals(tx.currency, payload.firstValue.currency)
    }

    @Test
    fun pending() {
        // GIVEN
        val paymentResponse = CreateTransferResponse("111", null, Status.PENDING)
        doReturn(paymentResponse).whenever(mtnGateway).createTransfer(any())

        // WHEN
        val request = CreateCashinRequest(
            paymentMethodToken = "11111",
            amount = 50000.0,
            currency = "XAF"
        )
        val response = rest.postForEntity(url, request, CreateCashinResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        assertEquals(Status.PENDING.name, response.body.status)

        val tx = txDao.findById(response.body.id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.userId)
        assertEquals(request.currency, tx.currency)
        assertEquals(request.amount, tx.amount)
        assertEquals(request.paymentMethodToken, tx.paymentMethodToken)
        assertEquals(PaymentMethodProvider.MTN, tx.paymentMethodProvider)
        assertEquals(TransactionType.CASHOUT, tx.type)
        assertEquals(Status.PENDING, tx.status)
        assertEquals(paymentResponse.transactionId, tx.gatewayTransactionId)
        assertEquals(paymentResponse.financialTransactionId, tx.financialTransactionId)
        assertNull(tx.supplierErrorCode)
        assertNull(tx.description)
        assertNull(tx.errorCode)

        val records = recordDao.findByTransaction(tx)
        assertEquals(0, records.size)

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    fun failure() {
        // GIVEN
        val e =
            PaymentException(error = Error(code = NOT_ENOUGH_FUNDS, transactionId = "111", supplierErrorCode = "xxxx"))
        doThrow(e).whenever(mtnGateway).createTransfer(any())

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

        val txId = response.error.data?.get("id").toString()
        val tx = txDao.findById(txId).get()
        assertEquals(USER_ID, tx.userId)
        assertEquals(request.currency, tx.currency)
        assertEquals(request.amount, tx.amount)
        assertEquals(request.paymentMethodToken, tx.paymentMethodToken)
        assertEquals(PaymentMethodProvider.MTN, tx.paymentMethodProvider)
        assertEquals(TransactionType.CASHOUT, tx.type)
        assertEquals(Status.FAILED, tx.status)
        assertNull(tx.financialTransactionId)
        assertNull(tx.description)
        assertEquals(e.error.supplierErrorCode, tx.supplierErrorCode)
        assertEquals(e.error.code.name, tx.errorCode)
        assertEquals(e.error.transactionId, tx.gatewayTransactionId)

        val records = recordDao.findByTransaction(tx)
        assertEquals(0, records.size)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_FAILED.urn), payload.capture())
        assertEquals(-1, payload.firstValue.userId)
        assertEquals(TransactionType.CASHOUT.name, payload.firstValue.type)
        assertEquals(-1, payload.firstValue.recipientId)
        assertEquals(tx.id, payload.firstValue.transactionId)
        assertEquals(tx.tenantId, payload.firstValue.tenantId)
        assertEquals(tx.amount, payload.firstValue.amount)
        assertEquals(tx.currency, payload.firstValue.currency)
    }

    @Test
    fun badCurrency() {
        // WHEN
        val request = CreateCashoutRequest(
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
    }

    @Test
    public fun notEnoughFunds() {
        // WHEN
        val request = CreateCashoutRequest(
            amount = 50000000.0,
            currency = "XAF",
            paymentMethodToken = "xxxx"
        )
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateCashoutResponse::class.java)
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
        assertEquals(request.paymentMethodToken, tx.paymentMethodToken)
        assertEquals(PaymentMethodProvider.MTN, tx.paymentMethodProvider)
        assertEquals(TransactionType.CASHOUT, tx.type)
        assertEquals(Status.FAILED, tx.status)
        assertNull(tx.description)
        assertTrue(tx.gatewayTransactionId.isNullOrBlank())
        assertNull(tx.financialTransactionId)
        assertEquals(ErrorCode.NOT_ENOUGH_FUNDS.name, tx.errorCode)
        assertNull(tx.supplierErrorCode)

        val records = recordDao.findByTransaction(tx)
        assertEquals(0, records.size)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_FAILED.urn), payload.capture())
        assertEquals(-1, payload.firstValue.userId)
        assertEquals(TransactionType.CASHOUT.name, payload.firstValue.type)
        assertEquals(-1, payload.firstValue.recipientId)
        assertEquals(tx.id, payload.firstValue.transactionId)
        assertEquals(tx.tenantId, payload.firstValue.tenantId)
        assertEquals(tx.amount, payload.firstValue.amount)
        assertEquals(tx.currency, payload.firstValue.currency)
    }
}
