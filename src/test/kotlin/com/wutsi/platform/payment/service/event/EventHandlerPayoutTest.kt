package com.wutsi.platform.payment.service.event

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.Account
import com.wutsi.platform.account.dto.GetAccountResponse
import com.wutsi.platform.account.dto.GetPaymentMethodResponse
import com.wutsi.platform.account.dto.ListPaymentMethodResponse
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.account.dto.PaymentMethodSummary
import com.wutsi.platform.account.dto.Phone
import com.wutsi.platform.core.stream.Event
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.Gateway
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodProvider.MTN
import com.wutsi.platform.payment.PaymentMethodType
import com.wutsi.platform.payment.PaymentMethodType.MOBILE_PAYMENT
import com.wutsi.platform.payment.PaymentMethodType.UNKNOWN
import com.wutsi.platform.payment.core.Error
import com.wutsi.platform.payment.core.ErrorCode.NOT_ENOUGH_FUNDS
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.PayoutRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.model.CreateTransferResponse
import com.wutsi.platform.payment.model.GetTransferResponse
import com.wutsi.platform.payment.service.GatewayProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.jdbc.Sql
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/EventHandlerPayout.sql"])
internal class EventHandlerPayoutTest {
    @MockBean
    lateinit var gatewayProvider: GatewayProvider

    @MockBean
    private lateinit var eventStream: EventStream

    @MockBean
    private lateinit var accountApi: WutsiAccountApi

    @Autowired
    private lateinit var txDao: TransactionRepository

    private lateinit var account: Account

    private lateinit var gateway: Gateway

    private lateinit var paymentMethod: PaymentMethod

    private lateinit var paymentMethods: List<PaymentMethodSummary>

    @Autowired
    lateinit var handler: EventHandler

    @Autowired
    lateinit var payoutDao: PayoutRepository

    @BeforeEach
    fun setUp() {
        gateway = mock()
        doReturn(gateway).whenever(gatewayProvider).get(any())

        account = createAccount(111)
        doReturn(GetAccountResponse(account)).whenever(accountApi).getAccount(any())

        paymentMethod = createMethodPayment("3409430943", "+23799505677")
        paymentMethods = listOf(
            createMethodPaymentSummary("3409430943"),
            createMethodPaymentSummary("3409430000", type = UNKNOWN)
        )
        doReturn(GetPaymentMethodResponse(paymentMethod)).whenever(accountApi).getPaymentMethod(any(), any())
        doReturn(ListPaymentMethodResponse(paymentMethods)).whenever(accountApi).listPaymentMethods(any())
    }

    @Test
    fun `PENDING Payout - PAYOUT_PENDING - GetTransfer SUCCESSFUL`() {
        val paymentResponse = createGetTransferResponse(Status.SUCCESSFUL)
        doReturn(paymentResponse).whenever(gateway).getTransfer(any())

        val payoutId = "400"
        val event = createPayoutEvent(EventURN.PAYOUT_PENDING.urn, payoutId)
        handler.onEvent(event)

        val payout = payoutDao.findById(payoutId).get()
        assertEquals(Status.SUCCESSFUL, payout.status)
        assertEquals(paymentResponse.financialTransactionId, payout.financialTransactionId)
        assertNull(payout.errorCode)
        assertNull(payout.supplierErrorCode)
        assertEquals(100, payout.userId)

        verify(eventStream).enqueue(EventURN.PAYOUT_SUCCESSFUL.urn, PayoutEventPayload(payoutId))
    }

    @Test
    fun `PENDING Payout - PAYOUT_PENDING - GetTransfer PENDING`() {
        val paymentResponse = createGetTransferResponse(Status.PENDING)
        doReturn(paymentResponse).whenever(gateway).getTransfer(any())

        val payoutId = "401"
        val event = createPayoutEvent(EventURN.PAYOUT_PENDING.urn, payoutId)
        handler.onEvent(event)

        val payout = payoutDao.findById(payoutId).get()
        assertEquals(Status.PENDING, payout.status)

        verify(eventStream, never()).enqueue(any(), any())
    }

    @Test
    fun `PENDING Payout - PAYOUT_PENDING - GetTransfer FAILURE`() {
        val ex = PaymentException(
            error = Error(
                code = NOT_ENOUGH_FUNDS,
                supplierErrorCode = "YYYY"
            )
        )
        doThrow(ex).whenever(gateway).getTransfer(any())

        val payoutId = "402"
        val event = createPayoutEvent(EventURN.PAYOUT_PENDING.urn, payoutId)
        handler.onEvent(event)

        val payout = payoutDao.findById(payoutId).get()
        assertEquals(Status.FAILED, payout.status)
        assertEquals(ex.error.code, payout.errorCode)
        assertEquals(ex.error.supplierErrorCode, payout.supplierErrorCode)

        verify(eventStream).enqueue(EventURN.PAYOUT_FAILED.urn, PayoutEventPayload(payoutId))
    }

    @Test
    fun `SUCCESSFUL Payout - PAYOUT_SUCCESSFUL - GetTransfer SUCCESSFUL`() {
        val payoutId = "200"
        val event = createPayoutEvent(EventURN.PAYOUT_SUCCESSFUL.urn, payoutId)
        handler.onEvent(event)

        val payout = payoutDao.findById(payoutId).get()
        val txs = txDao.findByReferenceId(payoutId)
        assertEquals(1, txs.size)
        assertEquals(TransactionType.PAYOUT, txs[0].type)
        assertEquals(-payout.amount, txs[0].amount)
        assertEquals(payout.currency, txs[0].currency)
        assertEquals(payout.accountId, txs[0].accountId)
        assertEquals(payout.created, txs[0].created)
        assertEquals(payout.paymentMethodProvider, txs[0].paymentMethodProvider)

        verify(eventStream).publish(EventURN.PAYOUT_SUCCESSFUL.urn, PayoutEventPayload(payoutId))
    }

    @Test
    fun `PENDING Payout - PAYOUT_SUCCESSFUL - GetTransfer SUCCESSFUL`() {
        val payoutId = "201"
        val event = createPayoutEvent(EventURN.PAYOUT_SUCCESSFUL.urn, payoutId)
        handler.onEvent(event)

        val payout = payoutDao.findById(payoutId).get()
        val txs = txDao.findByReferenceId(payoutId)
        assertEquals(1, txs.size)
        assertEquals(TransactionType.PAYOUT, txs[0].type)
        assertEquals(-payout.amount, txs[0].amount)
        assertEquals(payout.currency, txs[0].currency)
        assertEquals(payout.accountId, txs[0].accountId)
        assertEquals(payout.created, txs[0].created)
        assertEquals(payout.paymentMethodProvider, txs[0].paymentMethodProvider)

        verify(eventStream).publish(EventURN.PAYOUT_SUCCESSFUL.urn, PayoutEventPayload(payoutId))
    }

    @Test
    fun `FAILED Payout - PAYOUT_FAILED`() {
        val payoutId = "777"
        val event = createPayoutEvent(EventURN.PAYOUT_FAILED.urn, payoutId)
        handler.onEvent(event)

        verify(eventStream).publish(EventURN.PAYOUT_FAILED.urn, PayoutEventPayload(payoutId))
    }

    private fun createBalanceEvent(type: String, id: Long, paymentMethodProvider: PaymentMethodProvider = MTN) = Event(
        type = type,
        payload = """
            {
                "accountId": "$id",
                "paymentMethodProvider": "${paymentMethodProvider.name}"
            }
        """.trimIndent()
    )

    private fun createPayoutEvent(type: String, payoutId: String) = Event(
        type = type,
        payload = """
            {
                "payoutId": "$payoutId"
            }
        """.trimIndent()
    )

    private fun createCreateTransferResponse(status: Status) = CreateTransferResponse(
        status = status,
        financialTransactionId = UUID.randomUUID().toString()
    )

    private fun createGetTransferResponse(status: Status) = GetTransferResponse(
        status = status,
        financialTransactionId = UUID.randomUUID().toString()
    )

    private fun createMethodPayment(
        token: String,
        phoneNumber: String = "",
        country: String = "CM",
        paymentMethodProvider: PaymentMethodProvider = MTN
    ) = PaymentMethod(
        token = token,
        phone = Phone(
            number = phoneNumber,
            country = country
        ),
        type = MOBILE_PAYMENT.name,
        provider = paymentMethodProvider.name
    )

    private fun createMethodPaymentSummary(
        token: String,
        type: PaymentMethodType = MOBILE_PAYMENT,
        paymentMethodProvider: PaymentMethodProvider = MTN
    ) = PaymentMethodSummary(
        token = token,
        type = type.name,
        provider = paymentMethodProvider.name
    )

    private fun createAccount(id: Long, status: String = "ACTIVE") = Account(
        id = id,
        status = status,
        displayName = "Ray Sponsible"
    )
}
