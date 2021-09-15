package com.wutsi.platform.payment.endpoint

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.payment.dto.GetBalanceResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.jdbc.Sql
import org.springframework.web.client.RestTemplate
import java.text.SimpleDateFormat
import java.time.Clock
import java.time.LocalDate
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/GetBalanceController.sql"])
public class GetBalanceControllerTest : AbstractSecuredController() {
    @LocalServerPort
    public val port: Int = 0

    @MockBean
    private lateinit var clock: Clock

    private lateinit var url: String
    private lateinit var rest: RestTemplate

    @BeforeEach
    override fun setUp() {
        super.setUp()

        val date = SimpleDateFormat("yyyy-MM-dd").parse("2020-02-15")
        doReturn(date.time).whenever(clock).millis()

        rest = createResTemplate(listOf("payment-read"), 1)
    }

    @Test
    fun `get balance - with previous balance`() {
        url = "http://localhost:$port/v1/balances?account-id=1"
        val response = rest.getForEntity(url, GetBalanceResponse::class.java)

        assertEquals(200, response.statusCodeValue)

        assertEquals("XAF", response.body.balance.currency)
        assertEquals(1, response.body.balance.accountId)
        assertEquals(12700.0, response.body.balance.amount)
        assertEquals(LocalDate.of(2020, 2, 1), response.body.balance.synced)
    }

    @Test
    fun `get balance - without previous balance`() {
        url = "http://localhost:$port/v1/balances?account-id=2"
        val response = rest.getForEntity(url, GetBalanceResponse::class.java)

        assertEquals(200, response.statusCodeValue)

        assertEquals("XAF", response.body.balance.currency)
        assertEquals(2, response.body.balance.accountId)
        assertEquals(9900.0, response.body.balance.amount)
        assertEquals(LocalDate.now(), response.body.balance.synced)
    }
}
