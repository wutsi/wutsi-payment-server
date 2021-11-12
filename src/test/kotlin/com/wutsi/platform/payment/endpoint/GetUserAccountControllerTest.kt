package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.dto.GetAccountResponse
import com.wutsi.platform.payment.entity.AccountType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.jdbc.Sql
import org.springframework.web.client.HttpClientErrorException
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/GetUserAccountController.sql"])
public class GetUserAccountControllerTest : AbstractSecuredController() {
    @LocalServerPort
    public val port: Int = 0

    @Test
    public fun invoke() {
        // WHEN
        val url = "http://localhost:$port/v1/users/$USER_ID/account"
        val response = rest.getForEntity(url, GetAccountResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        val account = response.body.account
        assertEquals(100000.0, account.balance)
        assertEquals("XAF", account.currency)
        assertEquals(AccountType.LIABILITY.name, account.type)
        assertEquals("Ray Sponsible", account.name)
    }

    @Test
    public fun notFound() {
        // WHEN
        val url = "http://localhost:$port/v1/users/9999/account"
        val ex = assertThrows<HttpClientErrorException> {
            rest.getForEntity(url, GetAccountResponse::class.java)
        }

        // THEN
        assertEquals(404, ex.rawStatusCode)
    }
}
