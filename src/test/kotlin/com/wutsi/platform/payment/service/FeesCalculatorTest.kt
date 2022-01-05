package com.wutsi.platform.payment.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.AccountSummary
import com.wutsi.platform.account.dto.SearchAccountResponse
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

    private lateinit var accountApi: WutsiAccountApi
    private lateinit var calculator: FeesCalculator
    private lateinit var tenant: Tenant

    @BeforeEach
    fun setUp() {
        accountApi = mock()
        calculator = FeesCalculator(accountApi)

        tenant = Tenant(
            fees = listOf(
                Fee(
                    transactionType = "transfer",
                    applyToSender = true,
                    retail = true,
                    business = null,
                    amount = 0.0,
                    percent = 0.02
                ),
                Fee(
                    transactionType = "transfer",
                    applyToSender = true,
                    retail = null,
                    business = null,
                    amount = 100.0,
                    percent = 0.0
                ),
                Fee(
                    transactionType = "transfer",
                    applyToSender = false,
                    retail = false,
                    business = null,
                    amount = 0.0,
                    percent = 0.02
                ),
            )
        )
    }

    @Test
    fun sendToRetail() {
        // GIVEN
        val accounts = listOf(AccountSummary(id = ACCOUNT_ID), AccountSummary(id = RECIPIENT_ID, retail = true))
        doReturn(SearchAccountResponse(accounts)).whenever(accountApi).searchAccount(any())

        val tx = TransactionEntity(
            type = TransactionType.TRANSFER,
            accountId = ACCOUNT_ID,
            recipientId = RECIPIENT_ID,
            amount = 50000.0,
        )

        // WHEN
        val fees = calculator.computeFees(tx, tenant)

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
        doReturn(SearchAccountResponse(accounts)).whenever(accountApi).searchAccount(any())

        val tx = TransactionEntity(
            type = TransactionType.TRANSFER,
            accountId = ACCOUNT_ID,
            recipientId = RECIPIENT_ID,
            amount = 50000.0,
        )

        // WHEN
        val fees = calculator.computeFees(tx, tenant)

        assertEquals(100.0, fees)
        assertEquals(50000.0, tx.amount)
        assertEquals(100.0, tx.fees)
        assertEquals(49900.0, tx.net)
        assertEquals(true, tx.feesToSender)
    }
}
