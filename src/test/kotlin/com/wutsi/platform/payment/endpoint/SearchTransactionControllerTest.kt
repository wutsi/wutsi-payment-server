package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.dto.SearchTransactionRequest
import com.wutsi.platform.payment.dto.SearchTransactionResponse
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.jdbc.Sql
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/SearchTransactionController.sql"])
public class SearchTransactionControllerTest : AbstractSecuredController() {
    @LocalServerPort
    public val port: Int = 0

    @Test
    public fun searcbByAccountId() {
        // WHEN
        val request = SearchTransactionRequest(
            limit = 30,
            offset = 0,
            accountId = 1L,
        )
        val url = "http://localhost:$port/v1/transactions/search"
        val response = rest.postForEntity(url, request, SearchTransactionResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        val txs = response.body.transactions
        assertEquals(2, txs.size)

        assertEquals("2", txs[0].id)
        assertEquals("1", txs[1].id)
    }


    @Test
    public fun searcbByPaymentRequestId() {
        // WHEN
        val request = SearchTransactionRequest(
            limit = 30,
            offset = 0,
            paymentRequestId = "REQ-3",
        )
        val url = "http://localhost:$port/v1/transactions/search"
        val response = rest.postForEntity(url, request, SearchTransactionResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        val txs = response.body.transactions
        assertEquals(1, txs.size)

        assertEquals("30", txs[0].id)
    }
}
