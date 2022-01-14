package com.wutsi.platform.payment.service

import com.wutsi.platform.account.dto.AccountSummary
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType
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

    @BeforeEach
    fun setUp() {
        calculator = FeesCalculator()

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
                    transactionType = "payment",
                    applyToSender = false,
                    amount = 0.0,
                    percent = 0.02
                ),
                Fee(
                    transactionType = "cashout",
                    applyToSender = true,
                    business = true,
                    amount = 0.0,
                    percent = 0.01
                ),
                Fee(
                    transactionType = "cashout",
                    applyToSender = true,
                    business = true,
                    retail = true,
                    amount = 0.0,
                    percent = 0.02
                ),
            )
        )
    }

    @Test
    fun transferBelowThreshold() {
        // GIVEN
        val accounts = listOf(AccountSummary(id = ACCOUNT_ID), AccountSummary(id = RECIPIENT_ID, business = true))
            .map { it.id to it }.toMap()

        val tx = TransactionEntity(
            type = TransactionType.TRANSFER,
            accountId = ACCOUNT_ID,
            recipientId = RECIPIENT_ID,
            amount = 1000.0,
        )

        // WHEN
        val fees = calculator.computeFees(tx, tenant, accounts)

        // THEN
        assertEquals(0.0, fees)
        assertEquals(1000.0, tx.amount)
        assertEquals(0.0, tx.fees)
        assertEquals(1000.0, tx.net)
        assertEquals(false, tx.feesToSender)
    }

    @Test
    fun transferAboveThreshold() {
        // GIVEN
        val accounts = listOf(AccountSummary(id = ACCOUNT_ID), AccountSummary(id = RECIPIENT_ID, business = true))
            .map { it.id to it }.toMap()

        val tx = TransactionEntity(
            type = TransactionType.TRANSFER,
            accountId = ACCOUNT_ID,
            recipientId = RECIPIENT_ID,
            amount = 50000.0,
        )

        // WHEN
        val fees = calculator.computeFees(tx, tenant, accounts)

        // THEN
        assertEquals(100.0, fees)
        assertEquals(50100.0, tx.amount)
        assertEquals(100.0, tx.fees)
        assertEquals(50000.0, tx.net)
        assertEquals(true, tx.feesToSender)
    }

    @Test
    fun payment() {
        // GIVEN
        val accounts = listOf(AccountSummary(id = ACCOUNT_ID), AccountSummary(id = RECIPIENT_ID, business = true))
            .map { it.id to it }.toMap()

        val tx = TransactionEntity(
            type = TransactionType.PAYMENT,
            accountId = ACCOUNT_ID,
            recipientId = RECIPIENT_ID,
            amount = 50000.0,
        )

        // WHEN
        val fees = calculator.computeFees(tx, tenant, accounts)

        // THEN
        assertEquals(1000.0, fees)
        assertEquals(50000.0, tx.amount)
        assertEquals(1000.0, tx.fees)
        assertEquals(49000.0, tx.net)
        assertEquals(false, tx.feesToSender)
    }

    @Test
    fun cashout() {
        // GIVEN
        val accounts =
            listOf(AccountSummary(id = ACCOUNT_ID), AccountSummary(id = RECIPIENT_ID, business = true, retail = false))
                .map { it.id to it }.toMap()

        val tx = TransactionEntity(
            type = TransactionType.CASHOUT,
            accountId = ACCOUNT_ID,
            recipientId = RECIPIENT_ID,
            amount = 50000.0,
        )

        // WHEN
        val fees = calculator.computeFees(tx, tenant, accounts)

        // THEN
        assertEquals(500.0, fees)
        assertEquals(50500.0, tx.amount)
        assertEquals(500.0, tx.fees)
        assertEquals(50000.0, tx.net)
        assertEquals(true, tx.feesToSender)
    }

    @Test
    fun cashoutFromRetailer() {
        // GIVEN
        val accounts =
            listOf(AccountSummary(id = ACCOUNT_ID), AccountSummary(id = RECIPIENT_ID, business = true, retail = true))
                .map { it.id to it }.toMap()

        val tx = TransactionEntity(
            type = TransactionType.CASHOUT,
            accountId = ACCOUNT_ID,
            recipientId = RECIPIENT_ID,
            amount = 50000.0,
        )

        // WHEN
        val fees = calculator.computeFees(tx, tenant, accounts)

        // THEN
        assertEquals(1000.0, fees)
        assertEquals(51000.0, tx.amount)
        assertEquals(1000.0, tx.fees)
        assertEquals(50000.0, tx.net)
        assertEquals(true, tx.feesToSender)
    }

    @Test
    fun cashin() {
        // GIVEN
        val accounts =
            listOf(AccountSummary(id = ACCOUNT_ID), AccountSummary(id = RECIPIENT_ID, business = true, retail = false))
                .map { it.id to it }.toMap()

        val tx = TransactionEntity(
            type = TransactionType.CASHIN,
            accountId = ACCOUNT_ID,
            recipientId = RECIPIENT_ID,
            amount = 50000.0,
        )

        // WHEN
        val fees = calculator.computeFees(tx, tenant, accounts)

        // THEN
        assertEquals(0.0, fees)
        assertEquals(50000.0, tx.amount)
        assertEquals(0.0, tx.fees)
        assertEquals(50000.0, tx.net)
        assertEquals(false, tx.feesToSender)
    }
}
