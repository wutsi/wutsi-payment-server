package com.wutsi.platform.payment.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.AccountSummary
import com.wutsi.platform.account.dto.GetPaymentMethodResponse
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.payment.Gateway
import com.wutsi.platform.payment.GatewayProvider
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodType
import com.wutsi.platform.payment.core.Money
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.model.GetFeesResponse
import com.wutsi.platform.tenant.dto.Fee
import com.wutsi.platform.tenant.dto.Tenant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class FeesCalculatorTest {
    companion object {
        const val ACCOUNT_ID = 1L
        const val RECIPIENT_ID = 111L
    }

    private lateinit var calculator: FeesCalculator
    private lateinit var tenant: Tenant
    private lateinit var gateway: Gateway
    private lateinit var gatewayProvider: GatewayProvider
    private lateinit var securityManager: SecurityManager
    private lateinit var accountApi: WutsiAccountApi
    private val paymentMethod =
        PaymentMethod(token = "111", provider = PaymentMethodProvider.MTN.name, type = PaymentMethodType.MOBILE.name)

    @BeforeEach
    fun setUp() {
        securityManager = mock()
        doReturn(111L).whenever(securityManager).currentUserId()

        val payment = PaymentMethod(
            type = PaymentMethodType.MOBILE.name,
            provider = PaymentMethodProvider.MTN.name
        )
        accountApi = mock()
        doReturn(GetPaymentMethodResponse(payment)).whenever(accountApi).getPaymentMethod(any(), any())

        gateway = mock()
        doReturn(GetFeesResponse(fees = Money(100.0))).whenever(gateway).getFees(any())

        gatewayProvider = mock()
        doReturn(gateway).whenever(gatewayProvider).get(any())

        calculator = FeesCalculator(gatewayProvider, accountApi, securityManager)

        tenant = Tenant(
            fees = listOf(
                Fee(
                    transactionType = "transfer",
                    applyToSender = true,
                    amount = 100.0,
                    percent = 0.0,
                    threshold = 5000.0
                ),
                Fee(
                    transactionType = "transfer",
                    applyToSender = true,
                    toRetail = true,
                    amount = 0.0,
                    percent = 0.02
                ),
                Fee(
                    transactionType = "transfer",
                    applyToSender = true,
                    fromRetail = true,
                    amount = 0.0,
                    percent = 0.00
                ),
                Fee(
                    transactionType = "transfer",
                    applyToSender = false,
                    toBusiness = true,
                    amount = 0.0,
                    percent = 0.05
                ),
                Fee(
                    transactionType = "cashout",
                    applyToSender = true,
                    amount = 0.0,
                    percent = 0.01
                ),
            )
        )
    }

    @Test
    fun transferFromRetail() {
        // GIVEN
        val accounts =
            listOf(AccountSummary(id = ACCOUNT_ID, retail = true), AccountSummary(id = RECIPIENT_ID))
                .map { it.id to it }.toMap()

        val tx = TransactionEntity(
            type = TransactionType.TRANSFER,
            accountId = ACCOUNT_ID,
            recipientId = RECIPIENT_ID,
            amount = 50000.0,
        )

        // WHEN
        calculator.computeFees(tx, tenant, accounts, null)

        // THEN
        assertEquals(50000.0, tx.amount)
        assertEquals(0.0, tx.fees)
        assertEquals(50000.0, tx.net)
        assertEquals(0.0, tx.gatewayFees)
        assertEquals(true, tx.feesToSender)
    }

    @Test
    fun transferToRetail() {
        // GIVEN
        val accounts =
            listOf(AccountSummary(id = ACCOUNT_ID), AccountSummary(id = RECIPIENT_ID, retail = true))
                .map { it.id to it }.toMap()

        val tx = TransactionEntity(
            type = TransactionType.TRANSFER,
            accountId = ACCOUNT_ID,
            recipientId = RECIPIENT_ID,
            amount = 50000.0,
        )

        // WHEN
        calculator.computeFees(tx, tenant, accounts, null)

        // THEN
        assertEquals(51000.0, tx.amount)
        assertEquals(1000.0, tx.fees)
        assertEquals(50000.0, tx.net)
        assertEquals(0.0, tx.gatewayFees)
        assertEquals(true, tx.feesToSender)
    }

    @Test
    fun transferToBusiness() {
        // GIVEN
        val accounts =
            listOf(AccountSummary(id = ACCOUNT_ID), AccountSummary(id = RECIPIENT_ID, business = true))
                .map { it.id to it }.toMap()

        val tx = TransactionEntity(
            type = TransactionType.TRANSFER,
            accountId = ACCOUNT_ID,
            recipientId = RECIPIENT_ID,
            amount = 50000.0,
        )

        // WHEN
        calculator.computeFees(tx, tenant, accounts, null)

        // THEN
        assertEquals(50000.0, tx.amount)
        assertEquals(2500.0, tx.fees)
        assertEquals(47500.0, tx.net)
        assertEquals(0.0, tx.gatewayFees)
        assertEquals(false, tx.feesToSender)
    }

    @Test
    fun transferBelowThreshold() {
        // GIVEN
        val accounts = listOf(AccountSummary(id = ACCOUNT_ID), AccountSummary(id = RECIPIENT_ID, business = false))
            .map { it.id to it }.toMap()

        val tx = TransactionEntity(
            type = TransactionType.TRANSFER,
            accountId = ACCOUNT_ID,
            recipientId = RECIPIENT_ID,
            amount = 1000.0,
        )

        // WHEN
        calculator.computeFees(tx, tenant, accounts, null)

        // THEN
        assertEquals(1000.0, tx.amount)
        assertEquals(0.0, tx.fees)
        assertEquals(1000.0, tx.net)
        assertEquals(0.0, tx.gatewayFees)
        assertEquals(false, tx.feesToSender)
    }

    @Test
    fun transferAboveThreshold() {
        // GIVEN
        val accounts = listOf(AccountSummary(id = ACCOUNT_ID), AccountSummary(id = RECIPIENT_ID, business = false))
            .map { it.id to it }.toMap()

        val tx = TransactionEntity(
            type = TransactionType.TRANSFER,
            accountId = ACCOUNT_ID,
            recipientId = RECIPIENT_ID,
            amount = 50000.0,
        )

        // WHEN
        calculator.computeFees(tx, tenant, accounts, null)

        // THEN
        assertEquals(50100.0, tx.amount)
        assertEquals(100.0, tx.fees)
        assertEquals(50000.0, tx.net)
        assertEquals(0.0, tx.gatewayFees)
        assertEquals(true, tx.feesToSender)
    }

    @Test
    fun cashout() {
        // GIVEN
        val accounts =
            listOf(AccountSummary(id = ACCOUNT_ID))
                .map { it.id to it }.toMap()

        val tx = TransactionEntity(
            type = TransactionType.CASHOUT,
            accountId = ACCOUNT_ID,
            recipientId = null,
            amount = 50000.0,
        )

        // WHEN
        calculator.computeFees(tx, tenant, accounts, paymentMethod)

        // THEN
        assertEquals(50600.0, tx.amount)
        assertEquals(500.0, tx.fees)
        assertEquals(100.0, tx.gatewayFees)
        assertEquals(50000.0, tx.net)
        assertEquals(true, tx.feesToSender)
    }
}
