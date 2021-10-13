package com.wutsi.platform.payment.service.event

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.GetPaymentMethodResponse
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.account.dto.Phone
import com.wutsi.platform.core.stream.Event
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.Gateway
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodProvider.MTN
import com.wutsi.platform.payment.PaymentMethodType.MOBILE
import com.wutsi.platform.payment.core.Error
import com.wutsi.platform.payment.core.ErrorCode.EXPIRED
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.ChargeRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.model.GetPaymentResponse
import com.wutsi.platform.payment.service.GatewayProvider
import com.wutsi.platform.payment.service.TransactionService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.jdbc.Sql
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/EventHandlerCharge.sql"])
internal class EventHandlerChargeTest {
    companion object {
        const val PAYMENT_TOKEN = "xxx-000"
    }

    @MockBean
    lateinit var gatewayProvider: GatewayProvider

    @MockBean
    private lateinit var eventStream: EventStream

    @MockBean
    private lateinit var accountApi: WutsiAccountApi

    private lateinit var gateway: Gateway

    private lateinit var paymentMethod: PaymentMethod

    @Autowired
    lateinit var handler: EventHandler

    @Autowired
    lateinit var chargeDao: ChargeRepository

    @Autowired
    lateinit var txDao: TransactionRepository

    @BeforeEach
    fun setUp() {
        gateway = mock()
        doReturn(gateway).whenever(gatewayProvider).get(any())

        paymentMethod = createMethodPayment(PAYMENT_TOKEN, "+23799505677")
        doReturn(GetPaymentMethodResponse(paymentMethod)).whenever(accountApi).getPaymentMethod(any(), any())
    }

    @Test
    fun `PENDING Charge - CHARGE_PENDING - GetPayment SUCCESSFUL`() {
        val paymentResponse = createGetPaymentResponse(Status.SUCCESSFUL)
        doReturn(paymentResponse).whenever(gateway).getPayment(any())

        val chargeId = "100"
        val event = createChargeEvent(EventURN.CHARGE_PENDING.urn, chargeId)
        handler.onEvent(event)

        val charge = chargeDao.findById(chargeId).get()
        assertEquals(Status.SUCCESSFUL, charge.status)
        assertEquals(paymentResponse.financialTransactionId, charge.financialTransactionId)
        assertTrue(charge.updated.isAfter(charge.created))

        verify(eventStream).enqueue(EventURN.CHARGE_SUCCESSFUL.urn, ChargeEventPayload(chargeId))
    }

    @Test
    fun `PENDING Charge - CHARGE_PENDING - GetPayment FAILED`() {
        val ex = PaymentException(
            error = Error(
                code = EXPIRED,
                transactionId = UUID.randomUUID().toString(),
                supplierErrorCode = "xxx"
            )
        )
        doThrow(ex).whenever(gateway).getPayment(any())

        val chargeId = "101"
        val event = createChargeEvent(EventURN.CHARGE_PENDING.urn, chargeId)
        handler.onEvent(event)

        val charge = chargeDao.findById(chargeId).get()
        assertEquals(Status.FAILED, charge.status)
        assertEquals(ex.error.code, charge.errorCode)
        assertEquals(ex.error.supplierErrorCode, charge.supplierErrorCode)
        assertTrue(charge.updated.isAfter(charge.created))

        verify(eventStream).enqueue(EventURN.CHARGE_FAILED.urn, ChargeEventPayload(chargeId))
    }

    @Test
    fun `PENDING Charge - CHARGE_PENDING - GetPayment PENDING`() {
        val paymentResponse = createGetPaymentResponse(Status.PENDING)
        doReturn(paymentResponse).whenever(gateway).getPayment(any())

        val chargeId = "102"
        val event = createChargeEvent(EventURN.CHARGE_PENDING.urn, chargeId)
        handler.onEvent(event)

        val charge = chargeDao.findById(chargeId).get()
        assertEquals(Status.PENDING, charge.status)

        verify(eventStream, never()).enqueue(any(), any())
    }

    @Test
    fun `SUCESSFUL Charge - CHARGE_SUCCESSFUL`() {
        val chargeId = "200"
        val event = createChargeEvent(EventURN.CHARGE_SUCCESSFUL.urn, chargeId)
        handler.onEvent(event)

        val charge = chargeDao.findById(chargeId).get()
        val txs = txDao.findByReferenceId(chargeId).sortedBy { it.id }

        assertEquals(2, txs.size)
        assertEquals(TransactionType.CHARGE, txs[0].type)
        assertEquals(9800.0, txs[0].amount)
        assertEquals(charge.currency, txs[0].currency)
        assertEquals(charge.merchantId, txs[0].accountId)
        assertEquals(charge.description, txs[0].description)
        assertEquals(charge.created, txs[0].created)
        assertEquals(charge.paymentMethodProvider, txs[0].paymentMethodProvider)

        assertEquals(TransactionType.FEES, txs[1].type)
        assertEquals(200.0, txs[1].amount)
        assertEquals(charge.currency, txs[1].currency)
        assertEquals(TransactionService.FEES_ACCOUNT_ID, txs[1].accountId)
        assertNull(txs[1].description)
        assertEquals(charge.created, txs[0].created)
        assertEquals(charge.paymentMethodProvider, txs[1].paymentMethodProvider)

        verify(eventStream).publish(EventURN.CHARGE_SUCCESSFUL.urn, ChargeEventPayload(chargeId))
    }

    @Test
    fun `PENDING Payout - CHARGE_SUCCESSFUL twice`() {
        val chargeId = "201"
        val event = createChargeEvent(EventURN.CHARGE_SUCCESSFUL.urn, chargeId)
        handler.onEvent(event)
        handler.onEvent(event)

        val txs = txDao.findByReferenceId(chargeId).sortedBy { it.id }
        assertEquals(2, txs.size)
        assertEquals(TransactionType.CHARGE, txs[0].type)
        assertEquals(TransactionType.FEES, txs[1].type)

        verify(eventStream, times(1)).publish(EventURN.CHARGE_SUCCESSFUL.urn, ChargeEventPayload(chargeId))
    }

    @Test
    fun `FAILED Charge - CHARGE_FAILED`() {
        val chargeId = "7777"
        val event = createChargeEvent(EventURN.CHARGE_FAILED.urn, chargeId)
        handler.onEvent(event)

        verify(eventStream).publish(EventURN.CHARGE_FAILED.urn, ChargeEventPayload(chargeId))
    }

    private fun createChargeEvent(type: String, id: String) = Event(
        type = type,
        payload = """
            {
                "chargeId": "$id"
            }
        """.trimIndent()
    )

    private fun createGetPaymentResponse(status: Status) = GetPaymentResponse(
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
        type = MOBILE.name,
        provider = paymentMethodProvider.name
    )
}
