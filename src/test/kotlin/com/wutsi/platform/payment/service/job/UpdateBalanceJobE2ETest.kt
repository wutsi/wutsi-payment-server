package com.wutsi.platform.payment.service.job

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.Account
import com.wutsi.platform.account.dto.GetAccountResponse
import com.wutsi.platform.account.dto.GetPaymentMethodResponse
import com.wutsi.platform.account.dto.ListPaymentMethodResponse
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.account.dto.PaymentMethodSummary
import com.wutsi.platform.account.dto.Phone
import com.wutsi.platform.payment.Gateway
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodProvider.MTN
import com.wutsi.platform.payment.PaymentMethodType
import com.wutsi.platform.payment.PaymentMethodType.MOBILE_PAYMENT
import com.wutsi.platform.payment.PaymentMethodType.UNKNOWN
import com.wutsi.platform.payment.core.Error
import com.wutsi.platform.payment.core.ErrorCode
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.BalanceRepository
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
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
internal class UpdateBalanceJobE2ETest {
    @MockBean
    lateinit var gatewayProvider: GatewayProvider

    @MockBean
    private lateinit var accountApi: WutsiAccountApi

    private lateinit var gateway: Gateway

    @Autowired
    private lateinit var job: UpdateBalanceJob

    @Autowired
    private lateinit var balanceDao: BalanceRepository

    @Autowired
    private lateinit var txDao: TransactionRepository

    @Autowired
    private lateinit var payoutDao: PayoutRepository

    @BeforeEach
    fun setUp() {
        gateway = mock()
        doReturn(gateway).whenever(gatewayProvider).get(any())

        val createTransferResponse = createCreateTransferResponse(Status.PENDING)
        val getTransferResponse = createGetTransferResponse(Status.SUCCESSFUL)
        doReturn(createTransferResponse).whenever(gateway).createTransfer(any())
        doReturn(getTransferResponse).whenever(gateway).getTransfer(any())

        val account = createAccount(111)
        doReturn(GetAccountResponse(account)).whenever(accountApi).getAccount(any())

        val paymentMethod = createMethodPayment("3409430943", "+23799505677")
        val paymentMethods = listOf(
            createMethodPaymentSummary("3409430943"),
            createMethodPaymentSummary("3409430000", type = UNKNOWN)
        )
        doReturn(GetPaymentMethodResponse(paymentMethod)).whenever(accountApi).getPaymentMethod(any(), any())
        doReturn(ListPaymentMethodResponse(paymentMethods)).whenever(accountApi).listPaymentMethods(any())
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/UpdateBalanceJobE2E.sql"])
    fun success() {
        job.run()

        Thread.sleep(60000)

        val balance = balanceDao.findByAccountIdAndPaymentMethodProvider(3L, MTN).get()
        assertEquals(700.0, balance.amount)
        assertEquals("XAF", balance.currency)
        assertEquals(36, balance.payoutId.length)
        assertEquals(LocalDate.now(), balance.synced)

        val payout = payoutDao.findById(balance.payoutId).get()
        assertEquals(balance.amount, payout.amount)
        assertEquals(balance.currency, payout.currency)
        assertEquals(Status.SUCCESSFUL, payout.status)

        val txs = txDao.findByReferenceId(balance.payoutId)
        assertEquals(1, txs.size)
        assertEquals(balance.accountId, txs[0].accountId)
        assertEquals(TransactionType.PAYOUT, txs[0].type)
        assertEquals(MTN, txs[0].paymentMethodProvider)
        assertEquals(-balance.amount, txs[0].amount)
        assertEquals(balance.currency, txs[0].currency)
        assertNotNull(txs[0].created)
        assertNull(txs[0].description)
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/UpdateBalanceJobE2E.sql"])
    fun failure() {
        val ex = PaymentException(
            error = Error(
                code = ErrorCode.EXPIRED,
                supplierErrorCode = "TIMEOUT",
                transactionId = "xxxx"
            )
        )
        doThrow(ex).whenever(gateway).getTransfer(any())

        job.run()

        Thread.sleep(30000)

        val balance = balanceDao.findByAccountIdAndPaymentMethodProvider(3L, MTN).get()
        assertEquals(700.0, balance.amount)
        assertEquals("XAF", balance.currency)
        assertEquals(36, balance.payoutId.length)
        assertEquals(LocalDate.now(), balance.synced)

        val payout = payoutDao.findById(balance.payoutId).get()
        assertEquals(balance.amount, payout.amount)
        assertEquals(balance.currency, payout.currency)
        assertEquals(Status.FAILED, payout.status)
        assertEquals(ex.error.code, payout.errorCode)
        assertEquals(ex.error.supplierErrorCode, payout.supplierErrorCode)

        val txs = txDao.findByReferenceId(balance.payoutId)
        assertTrue(txs.isEmpty())
    }

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

    private fun createCreateTransferResponse(status: Status) = CreateTransferResponse(
        status = status,
        financialTransactionId = UUID.randomUUID().toString(),
    )

    private fun createGetTransferResponse(status: Status) = GetTransferResponse(
        status = status,
        financialTransactionId = UUID.randomUUID().toString()
    )
}
