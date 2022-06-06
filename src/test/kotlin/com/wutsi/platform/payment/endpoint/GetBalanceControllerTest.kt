package com.wutsi.platform.payment.endpoint

import com.fasterxml.jackson.databind.ObjectMapper
import com.wutsi.platform.core.error.ErrorResponse
import com.wutsi.platform.payment.dto.GetBalanceResponse
import com.wutsi.platform.payment.error.ErrorURN
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.jdbc.Sql
import org.springframework.web.client.HttpClientErrorException
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
        val ex = assertThrows<HttpClientErrorException> {
            rest.getForEntity(url, GetBalanceResponse::class.java)
        }

        // THEN
        assertEquals(404, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.BALANCE_NOT_FOUND.urn, response.error.code)
    }

    @Test
    public fun invalidTenant() {
        // WHEN
        val url = "http://localhost:$port/v1/accounts/555/balance"
        val ex = assertThrows<HttpClientErrorException> {
            rest.getForEntity(url, GetBalanceResponse::class.java)
        }

        // THEN
        assertEquals(403, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.ILLEGAL_TENANT_ACCESS.urn, response.error.code)
    }
}
