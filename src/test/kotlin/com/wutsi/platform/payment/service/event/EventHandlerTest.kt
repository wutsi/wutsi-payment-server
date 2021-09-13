package com.wutsi.platform.payment.service.event

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.core.stream.Event
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.Gateway
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.core.Error
import com.wutsi.platform.payment.core.ErrorCode.EXPIRED
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.ChargeRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.model.GetPaymentResponse
import com.wutsi.platform.payment.service.GatewayProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.jdbc.Sql
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/EventHandler.sql"])
internal class EventHandlerTest {
    @MockBean
    lateinit var gatewayProvider: GatewayProvider

    @MockBean
    private lateinit var eventStream: EventStream

    lateinit var gateway: Gateway

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
    }

    @Test
    fun `PENDING event on SUCCESSFUL charge`() {
        val paymentResponse = createGetPaymentResponse(Status.SUCCESSFUL)
        doReturn(paymentResponse).whenever(gateway).getPayment(any())

        val chargeId = "100"
        val event = createEvent(EventURN.CHARGE_PENDING.urn, chargeId)
        handler.onEvent(event)

        val charge = chargeDao.findById(chargeId).get()
        assertEquals(Status.SUCCESSFUL, charge.status)
        assertEquals(paymentResponse.financialTransactionId, charge.financialTransactionId)
        assertTrue(charge.updated.isAfter(charge.created))

        verify(eventStream).enqueue(EventURN.CHARGE_SUCCESSFUL.urn, ChargeEventPayload(chargeId))
    }

    @Test
    fun `PENDING event on FAILED charge`() {
        val ex = PaymentException(
            error = Error(
                code = EXPIRED,
                transactionId = UUID.randomUUID().toString(),
                supplierErrorCode = "xxx"
            )
        )
        doThrow(ex).whenever(gateway).getPayment(any())

        val chargeId = "101"
        val event = createEvent(EventURN.CHARGE_PENDING.urn, chargeId)
        handler.onEvent(event)

        val charge = chargeDao.findById(chargeId).get()
        assertEquals(Status.FAILED, charge.status)
        assertEquals(ex.error.code, charge.errorCode)
        assertEquals(ex.error.supplierErrorCode, charge.supplierErrorCode)
        assertTrue(charge.updated.isAfter(charge.created))

        verify(eventStream, never()).enqueue(any(), any())
    }

    @Test
    fun `PENDING event on PENDING charge`() {
        val paymentResponse = createGetPaymentResponse(Status.PENDING)
        doReturn(paymentResponse).whenever(gateway).getPayment(any())

        val chargeId = "102"
        val event = createEvent(EventURN.CHARGE_PENDING.urn, chargeId)
        handler.onEvent(event)

        val charge = chargeDao.findById(chargeId).get()
        assertEquals(Status.PENDING, charge.status)

        verify(eventStream, never()).enqueue(any(), any())
    }

    @Test
    fun `SUCCESSFUL event on PENDING charge`() {
        val paymentResponse = createGetPaymentResponse(Status.PENDING)
        doReturn(paymentResponse).whenever(gateway).getPayment(any())

        val chargeId = "200"
        val event = createEvent(EventURN.CHARGE_SUCCESSFUL.urn, chargeId)
        handler.onEvent(event)

        val charge = chargeDao.findById(chargeId).get()
        val tx = txDao.findById(chargeId).get()
        assertEquals(TransactionType.CHARGE, tx.type)
        assertEquals(10000.0, tx.amount)
        assertEquals(100.0, tx.fees)
        assertEquals(9900.0, tx.net)
        assertEquals(charge.currency, tx.currency)
        assertEquals(charge.customerId, tx.fromAccountId)
        assertEquals(charge.merchantId, tx.toAccountId)
    }

    @Test
    fun `SUCCESSFUL event on charge having already a TX`() {
        val paymentResponse = createGetPaymentResponse(Status.PENDING)
        doReturn(paymentResponse).whenever(gateway).getPayment(any())

        val chargeId = "201"
        val event = createEvent(EventURN.CHARGE_SUCCESSFUL.urn, chargeId)
        handler.onEvent(event)
        // No error
    }

    private fun createEvent(type: String, id: String) = Event(
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
}
