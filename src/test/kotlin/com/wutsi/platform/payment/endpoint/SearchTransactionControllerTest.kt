package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dto.SearchTransactionRequest
import com.wutsi.platform.payment.dto.SearchTransactionResponse
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.jdbc.Sql
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/SearchTransactionController.sql"])
public class SearchTransactionControllerTest : AbstractSecuredController() {
    @LocalServerPort
    public val port: Int = 0

    @Test
    public fun searchByAccountId() {
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

        val txs = response.body!!.transactions
        assertEquals(3, txs.size)

        assertEquals("3", txs[0].id)
        assertEquals("2", txs[1].id)
        assertEquals("1", txs[2].id)
    }

    @Test
    public fun searchByOrderId() {
        // WHEN
        val request = SearchTransactionRequest(
            limit = 30,
            offset = 0,
            orderId = "ORDER-4",
        )
        val url = "http://localhost:$port/v1/transactions/search"
        val response = rest.postForEntity(url, request, SearchTransactionResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        val txs = response.body!!.transactions
        assertEquals(1, txs.size)

        assertEquals("40", txs[0].id)
    }

    @Test
    public fun searchByStatus() {
        // WHEN
        val request = SearchTransactionRequest(
            limit = 30,
            status = listOf(Status.PENDING.name, Status.SUCCESSFUL.name)
        )
        val url = "http://localhost:$port/v1/transactions/search"
        val response = rest.postForEntity(url, request, SearchTransactionResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        val txs = response.body!!.transactions
        assertEquals(4, txs.size)
    }
}
