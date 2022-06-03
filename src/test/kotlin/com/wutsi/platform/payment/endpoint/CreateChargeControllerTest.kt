package com.wutsi.platform.payment.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.account.dto.AccountSummary
import com.wutsi.platform.account.dto.SearchAccountResponse
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.core.Money
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.BalanceRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.CreateChargeRequest
import com.wutsi.platform.payment.dto.CreateChargeResponse
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.event.TransactionEventPayload
import com.wutsi.platform.payment.model.CreatePaymentResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.jdbc.Sql
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            AccountSummary(id = USER_ID, status = "ACTIVE"),
            AccountSummary(id = RECIPIENT_ID, status = "ACTIVE")
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
        val request = CreateChargeRequest(
            paymentMethodToken = "11111",
            recipientId = RECIPIENT_ID,
            amount = 50000.0,
            currency = "XAF",
            description = "Hello world"
        )
        val response = rest.postForEntity(url, request, CreateChargeResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        assertEquals(Status.SUCCESSFUL.name, response.body!!.status)

        val fees = 0.0
        val tx = txDao.findById(response.body!!.id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.accountId)
        assertEquals(RECIPIENT_ID, tx.recipientId)
        assertEquals(request.currency, tx.currency)
        assertEquals(request.amount + fees, tx.amount)
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
        assertNull(tx.paymentRequestId)
        assertNull(tx.expires)
        assertFalse(tx.requiresApproval)
        assertNull(tx.approved)

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
        val request = CreateChargeRequest(
            paymentMethodToken = "11111",
            recipientId = RECIPIENT_ID,
            amount = 50000.0,
            currency = "XAF",
            description = "Hello world"
        )
        val response = rest.postForEntity(url, request, CreateChargeResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        assertEquals(Status.PENDING.name, response.body!!.status)

        val fees = 0.0
        val tx = txDao.findById(response.body!!.id).get()
        assertEquals(1L, tx.tenantId)
        assertEquals(USER_ID, tx.accountId)
        assertEquals(RECIPIENT_ID, tx.recipientId)
        assertEquals(request.currency, tx.currency)
        assertEquals(request.amount + fees, tx.amount)
        assertEquals(0.0, tx.fees)
        assertEquals(0.0, tx.gatewayFees)
        assertEquals(request.amount - fees, tx.net)
        assertEquals(request.paymentMethodToken, tx.paymentMethodToken)
        assertEquals(PaymentMethodProvider.MTN, tx.paymentMethodProvider)
        assertEquals(TransactionType.CHARGE, tx.type)
        assertEquals(Status.PENDING, tx.status)
        assertNull(tx.gatewayTransactionId)
        assertNull(tx.financialTransactionId)
        assertNull(tx.supplierErrorCode)
        assertEquals(request.description, tx.description)
        assertNull(tx.errorCode)
        assertNull(tx.paymentRequestId)
        assertNull(tx.expires)
        assertFalse(tx.requiresApproval)
        assertNull(tx.approved)

        assertEquals(5000.0, balanceDao.findByAccountId(USER_ID).get().amount)
        assertEquals(100000.0, balanceDao.findByAccountId(RECIPIENT_ID).get().amount)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_PENDING.urn), payload.capture())
        assertEquals(TransactionType.CHARGE.name, payload.firstValue.type)
        assertEquals(tx.id, payload.firstValue.transactionId)
    }
}
