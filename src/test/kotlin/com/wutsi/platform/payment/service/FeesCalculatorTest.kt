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
                    business = true,
                    amount = 0.0,
                    percent = 0.02
                ),
                Fee(
                    transactionType = "transfer",
                    applyToSender = true,
                    business = false,
                    amount = 100.0,
                    percent = 0.0
                ),
                Fee(
                    transactionType = "payment",
                    applyToSender = false,
                    business = true,
                    amount = 0.0,
                    percent = 0.04
                ),
            )
        )
    }

    @Test
    fun sendToBusiness() {
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
        assertEquals(1000.0, fees)
        assertEquals(50000.0, tx.amount)
        assertEquals(1000.0, tx.fees)
        assertEquals(49000.0, tx.net)
        assertEquals(true, tx.feesToSender)
    }

    @Test
    fun sendToPerson() {
        // GIVEN
        val accounts = listOf(AccountSummary(id = ACCOUNT_ID), AccountSummary(id = RECIPIENT_ID))
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
        assertEquals(50000.0, tx.amount)
        assertEquals(100.0, tx.fees)
        assertEquals(49900.0, tx.net)
        assertEquals(true, tx.feesToSender)
    }

    @Test
    fun payBusiness() {
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
        assertEquals(2000.0, fees)
        assertEquals(50000.0, tx.amount)
        assertEquals(2000.0, tx.fees)
        assertEquals(48000.0, tx.net)
        assertEquals(false, tx.feesToSender)
    }
}
