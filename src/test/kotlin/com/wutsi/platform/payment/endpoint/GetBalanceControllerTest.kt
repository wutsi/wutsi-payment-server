package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.dto.GetBalanceResponse
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.jdbc.Sql
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/GetBalanceController.sql"])
public class GetBalanceControllerTest : AbstractSecuredController() {
    @LocalServerPort
    public val port: Int = 0

    @Test
    public fun invoke() {
        // WHEN
        val url = "http://localhost:$port/v1/accounts/$USER_ID/balance"
        val response = rest.getForEntity(url, GetBalanceResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        val balance = response.body.balance
        assertEquals(100000.0, balance.amount)
        assertEquals("XAF", balance.currency)
        assertEquals(USER_ID, balance.userId)
    }

    @Test
    public fun notFound() {
        // WHEN
        val url = "http://localhost:$port/v1/accounts/999999/balance"
        val response = rest.getForEntity(url, GetBalanceResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        val balance = response.body.balance
        assertEquals(0.0, balance.amount)
        assertEquals("XAF", balance.currency)
        assertEquals(999999L, balance.userId)
    }
}
