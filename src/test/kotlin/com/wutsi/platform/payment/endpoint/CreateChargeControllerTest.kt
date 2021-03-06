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
import com.wutsi.platform.account.dto.GetAccountResponse
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
import com.wutsi.platform.payment.model.CreatePaymentRequest
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
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CreateChargeControllerTest : AbstractSecuredController() {
    companion object {
        const val RECIPIENT_ID = 100L
    }

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
        assertEquals(request.orderId, tx.orderId)
        assertEquals(request.idempotencyKey, tx.idempotencyKey)
        assertFalse(tx.applyFeesToSender)

        assertEquals(100000.0, balanceDao.findByAccountId(USER_ID).get().amount)
        assertEquals(200000.0 + tx.net, balanceDao.findByAccountId(RECIPIENT_ID).get().amount)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_SUCCESSFUL.urn), payload.capture())
        assertEquals(TransactionType.CHARGE.name, payload.firstValue.type)
        assertEquals(tx.id, payload.firstValue.transactionId)

        val req = argumentCaptor<CreatePaymentRequest>()
        verify(gateway).createPayment(req.capture())
        assertEquals(request.description, req.firstValue.description)
        assertNull(req.firstValue.payerMessage)
        assertEquals(tx.amount, req.firstValue.amount.value)
        assertEquals(tx.currency, req.firstValue.amount.currency)
        assertEquals(tx.id, req.firstValue.externalId)
        assertEquals(user.email, req.firstValue.payer.email)
        assertEquals(paymentMethod.ownerName, req.firstValue.payer.fullName)
        assertEquals(paymentMethod.phone!!.number, req.firstValue.payer.phoneNumber)
        assertEquals(paymentMethod.phone!!.country, req.firstValue.payer.country)
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateChargeController.sql"])
    fun successForUserWithNoEmail() {
        // GIVEN
        val user = createUser(email = null, phone = phone)
        doReturn(GetAccountResponse(user)).whenever(accountApi).getAccount(any())

        val gwFees = 100.0
        val paymentResponse = CreatePaymentResponse("111", "222", Status.SUCCESSFUL, Money(gwFees, "XAF"))
        doReturn(paymentResponse).whenever(gateway).createPayment(any())

        // WHEN
        val request = createRequest()
        val response = rest.postForEntity(url, request, CreateChargeResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        assertEquals(Status.SUCCESSFUL.name, response.body!!.status)

        val tx = txDao.findById(response.body!!.id).get()

        val req = argumentCaptor<CreatePaymentRequest>()
        verify(gateway).createPayment(req.capture())
        assertEquals(request.description, req.firstValue.description)
        assertNull(req.firstValue.payerMessage)
        assertEquals(tx.amount, req.firstValue.amount.value)
        assertEquals(tx.currency, req.firstValue.amount.currency)
        assertEquals(tx.id, req.firstValue.externalId)
        assertEquals("user.${user.id}@wutsi.com", req.firstValue.payer.email)
        assertEquals(paymentMethod.ownerName, req.firstValue.payer.fullName)
        assertEquals(paymentMethod.phone!!.number, req.firstValue.payer.phoneNumber)
        assertEquals(paymentMethod.phone!!.country, req.firstValue.payer.country)
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateChargeController.sql"])
    fun successIdempotency() {
        // GIVEN
        val gwFees = 100.0
        val paymentResponse = CreatePaymentResponse("111", "222", Status.SUCCESSFUL, Money(gwFees, "XAF"))
        doReturn(paymentResponse).whenever(gateway).createPayment(any())

        // WHEN
        val request = createRequest(idempotencyKey = "i-100")
        val response = rest.postForEntity(url, request, CreateChargeResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        assertEquals(Status.SUCCESSFUL.name, response.body!!.status)
        assertEquals("100", response.body!!.id)

        assertEquals(100000.0, balanceDao.findByAccountId(USER_ID).get().amount)
        assertEquals(200000.0, balanceDao.findByAccountId(RECIPIENT_ID).get().amount)

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateChargeController.sql"])
    fun successFromWallet() {
        // GIVEN

        // WHEN
        val request = createRequest(paymentMethodToken = null)
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
        assertEquals(0.0, tx.gatewayFees)
        assertEquals(request.amount - fees, tx.net)
        assertNull(tx.paymentMethodToken)
        assertNull(tx.paymentMethodProvider)
        assertEquals(TransactionType.CHARGE, tx.type)
        assertEquals(Status.SUCCESSFUL, tx.status)
        assertNull(tx.gatewayTransactionId)
        assertNull(tx.financialTransactionId)
        assertNull(tx.supplierErrorCode)
        assertEquals(request.description, tx.description)
        assertNull(tx.errorCode)
        assertEquals(request.orderId, tx.orderId)
        assertEquals(request.idempotencyKey, tx.idempotencyKey)
        assertFalse(tx.applyFeesToSender)

        assertEquals(100000.0 - request.amount, balanceDao.findByAccountId(USER_ID).get().amount)
        assertEquals(200000.0 + tx.net, balanceDao.findByAccountId(RECIPIENT_ID).get().amount)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_SUCCESSFUL.urn), payload.capture())
        assertEquals(TransactionType.CHARGE.name, payload.firstValue.type)
        assertEquals(tx.id, payload.firstValue.transactionId)

        verify(gateway, never()).createPayment(any())
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
        assertEquals(request.idempotencyKey, tx.idempotencyKey)
        assertFalse(tx.applyFeesToSender)

        assertEquals(100000.0, balanceDao.findByAccountId(USER_ID).get().amount)
        assertEquals(200000.0, balanceDao.findByAccountId(RECIPIENT_ID).get().amount)

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateChargeController.sql"])
    fun pendingIdempotency() {
        // GIVEN
        val gwFees = 100.0
        val paymentResponse = CreatePaymentResponse("111", "222", Status.SUCCESSFUL, Money(gwFees, "XAF"))
        doReturn(paymentResponse).whenever(gateway).createPayment(any())

        // WHEN
        val request = createRequest(idempotencyKey = "i-200")
        val response = rest.postForEntity(url, request, CreateChargeResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        assertEquals(Status.PENDING.name, response.body!!.status)
        assertEquals("200", response.body!!.id)

        assertEquals(100000.0, balanceDao.findByAccountId(USER_ID).get().amount)
        assertEquals(200000.0, balanceDao.findByAccountId(RECIPIENT_ID).get().amount)

        verify(eventStream, never()).publish(any(), any())
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
        val request = createRequest(amount = 1000.0)
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
        val fees = 100.0
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
        assertEquals(request.idempotencyKey, tx.idempotencyKey)
        assertFalse(tx.applyFeesToSender)

        assertEquals(100000.0, balanceDao.findByAccountId(USER_ID).get().amount)
        assertEquals(200000.0, balanceDao.findByAccountId(RECIPIENT_ID).get().amount)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_FAILED.urn), payload.capture())
        assertEquals(TransactionType.CHARGE.name, payload.firstValue.type)
        assertEquals(tx.id, payload.firstValue.transactionId)
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateChargeController.sql"])
    fun notEnoughFundsFromWallet() {
        // WHEN
        val request = createRequest(amount = 50000000.0, paymentMethodToken = null)
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateChargeResponse::class.java)
        }

        // THEN
        assertEquals(409, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.TRANSACTION_FAILED.urn, response.error.code)
        assertEquals(ErrorCode.NOT_ENOUGH_FUNDS.name, response.error.downstreamCode)

        val txId = response.error.data?.get("transaction-id")
        assertNull(txId)

        assertEquals(100000.0, balanceDao.findByAccountId(USER_ID).get().amount)
        assertEquals(200000.0, balanceDao.findByAccountId(RECIPIENT_ID).get().amount)

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/CreateChargeController.sql"])
    fun errorIdempotency() {
        // WHEN
        val request = createRequest(idempotencyKey = "i-300")
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, request, CreateChargeResponse::class.java)
        }

        // THEN
        assertEquals(409, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.TRANSACTION_FAILED.urn, response.error.code)
        assertEquals(ErrorCode.NOT_ENOUGH_FUNDS.name, response.error.downstreamCode)

        assertEquals(100000.0, balanceDao.findByAccountId(USER_ID).get().amount)
        assertEquals(200000.0, balanceDao.findByAccountId(RECIPIENT_ID).get().amount)

        verify(eventStream, never()).publish(any(), any())
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
        assertEquals(request.idempotencyKey, tx.idempotencyKey)
        assertFalse(tx.applyFeesToSender)

        assertEquals(100000.0, balanceDao.findByAccountId(USER_ID).get().amount)
        assertEquals(200000.0, balanceDao.findByAccountId(RECIPIENT_ID).get().amount)

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

    private fun createRequest(
        amount: Double = 50000.0,
        currency: String = "XAF",
        recipientId: Long = RECIPIENT_ID,
        idempotencyKey: String? = null,
        paymentMethodToken: String? = "11111"
    ) =
        CreateChargeRequest(
            paymentMethodToken = paymentMethodToken,
            recipientId = recipientId,
            amount = amount,
            currency = currency,
            description = "Hello world",
            idempotencyKey = idempotencyKey ?: UUID.randomUUID().toString(),
            orderId = "order-100"
        )
}
