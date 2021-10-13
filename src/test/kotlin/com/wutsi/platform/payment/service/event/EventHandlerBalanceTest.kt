package com.wutsi.platform.payment.service.event

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.GetPaymentMethodResponse
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.account.dto.Phone
import com.wutsi.platform.core.stream.Event
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.Gateway
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodProvider.MTN
import com.wutsi.platform.payment.PaymentMethodType.MOBILE
import com.wutsi.platform.payment.dao.BalanceRepository
import com.wutsi.platform.payment.service.GatewayProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.jdbc.Sql
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/EventHandlerBalance.sql"])
internal class EventHandlerBalanceTest {
    companion object {
        const val PAYMENT_TOKEN = "xxx-000"
    }

    @MockBean
    lateinit var gatewayProvider: GatewayProvider

    @MockBean
    private lateinit var accountApi: WutsiAccountApi

    @MockBean
    lateinit var eventStream: EventStream

    private lateinit var gateway: Gateway

    private lateinit var paymentMethod: PaymentMethod

    @Autowired
    lateinit var handler: EventHandler

    @Autowired
    lateinit var balanceDao: BalanceRepository

    @BeforeEach
    fun setUp() {
        gateway = mock()
        doReturn(gateway).whenever(gatewayProvider).get(any())

        paymentMethod = createMethodPayment(PAYMENT_TOKEN, "+23799505677")
        doReturn(GetPaymentMethodResponse(paymentMethod)).whenever(accountApi).getPaymentMethod(any(), any())
    }

    @Test
    fun `Update balance - No balance available`() {
        val event = createBalanceEvent(EventURN.BALANCE_UPDATE_REQUESTED.urn, 4L, MTN)
        handler.onEvent(event)

        val balance = balanceDao.findByAccountIdAndPaymentMethodProvider(4L, PaymentMethodProvider.MTN).get()
        assertEquals(90.0, balance.amount)
        assertEquals("XAF", balance.currency)
        assertEquals(4L, balance.accountId)
        assertEquals(LocalDate.now(), balance.synced)
        assertEquals(MTN, balance.paymentMethodProvider)
        assertNotNull(balance.accountId)
    }

    @Test
    fun `Update balance - Balance available`() {
        val event = createBalanceEvent(EventURN.BALANCE_UPDATE_REQUESTED.urn, 3L, MTN)
        handler.onEvent(event)

        val balance = balanceDao.findByAccountIdAndPaymentMethodProvider(3L, PaymentMethodProvider.MTN).get()
        assertEquals(700.0, balance.amount)
        assertEquals("XAF", balance.currency)
        assertEquals(3L, balance.accountId)
        assertEquals(LocalDate.now(), balance.synced)
        assertEquals(MTN, balance.paymentMethodProvider)
        assertNotNull(balance.accountId)
    }

    private fun createBalanceEvent(type: String, accountId: Long, paymentMethodProvider: PaymentMethodProvider) = Event(
        type = type,
        payload = """
            {
                "accountId": "$accountId",
                "paymentMethodProvider": "${paymentMethodProvider.name}"
            }
        """.trimIndent()
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
