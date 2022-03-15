package com.wutsi.platform.payment.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.account.dto.AccountSummary
import com.wutsi.platform.account.dto.SearchAccountResponse
import com.wutsi.platform.payment.dto.ComputeTransactionFeesRequest
import com.wutsi.platform.payment.dto.ComputeTransactionFeesResponse
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.service.FeesCalculator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.web.server.LocalServerPort
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ComputeTransactionFeesControllerTest : AbstractSecuredController() {
    @LocalServerPort
    public val port: Int = 0

    private lateinit var url: String

    @MockBean
    private lateinit var feesCalculator: FeesCalculator

    @BeforeEach
    override fun setUp() {
        super.setUp()

        url = "http://localhost:$port/v1/transactions/fees"
    }

    @Test
    public fun invoke() {
        // GIVEN
        val account = AccountSummary(id = USER_ID, status = "ACTIVE")
        val recipient = AccountSummary(id = 200, business = true, status = "ACTIVE")
        doReturn(SearchAccountResponse(listOf(account, recipient))).whenever(accountApi).searchAccount(any())

        val request = ComputeTransactionFeesRequest(
            amount = 1000.0,
            transactionType = TransactionType.TRANSFER.name,
            recipientId = 200
        )
        val result = ComputeTransactionFeesResponse(100.0, 10.0, true)
        doReturn(result).whenever(feesCalculator).computeFees(eq(request), any(), any())

        // WHEN
        val response = rest.postForEntity(url, request, ComputeTransactionFeesResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)
        assertEquals(result.fees, response.body?.fees)
        assertEquals(result.gatewayFees, response.body?.gatewayFees)
        assertEquals(result.applyToSender, response.body?.applyToSender)
    }
}
