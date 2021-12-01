package com.wutsi.platform.payment.endpoint

import com.fasterxml.jackson.databind.ObjectMapper
import com.wutsi.platform.core.error.ErrorResponse
import com.wutsi.platform.payment.dto.GetTransactionResponse
import com.wutsi.platform.payment.util.ErrorURN
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.jdbc.Sql
import org.springframework.web.client.HttpClientErrorException
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/GetTransactionController.sql"])
public class GetTransactionControllerTest : AbstractSecuredController() {
    @LocalServerPort
    public val port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    public fun invoke() {
        // WHEN
        val url = "http://localhost:$port/v1/transactions/1"
        val response = rest.getForEntity(url, GetTransactionResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        val tx = response.body.transaction
        assertEquals("XAF", tx.currency)
        assertEquals("CASHIN", tx.type)
        assertEquals("SUCCESSFUL", tx.status)
        assertEquals("sample transaction", tx.description)
        assertEquals("NO_ERROR", tx.errorCode)
        assertEquals("fin-1", tx.financialTransactionId)
        assertEquals("gw-1", tx.gatewayTransactionId)
        assertEquals("MTN", tx.paymentMethodProvider)
        assertEquals("ERR-0001", tx.supplierErrorCode)
        assertEquals("xxx", tx.paymentMethodToken)
        assertEquals(1000.0, tx.amount)
        assertEquals(100.0, tx.fees)
        assertEquals(900.0, tx.net)
        assertEquals(11L, tx.recipientId)
        assertEquals(1L, tx.accountId)
    }

    @Test
    public fun notFound() {
        // WHEN
        val url = "http://localhost:$port/v1/transactions/9999"
        val ex = assertThrows<HttpClientErrorException> {
            rest.getForEntity(url, GetTransactionResponse::class.java)
        }

        // THEN
        assertEquals(404, ex.rawStatusCode)

        val response = objectMapper.readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.TRANSACTION_NOT_FOUND.urn, response.error.code)
    }

    @Test
    public fun invalidUser() {
        // WHEN
        val url = "http://localhost:$port/v1/transactions/11"
        val ex = assertThrows<HttpClientErrorException> {
            rest.getForEntity(url, GetTransactionResponse::class.java)
        }

        // THEN
        assertEquals(403, ex.rawStatusCode)

        val response = objectMapper.readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.OWNERSHIP_ERROR.urn, response.error.code)
    }

    @Test
    public fun invalidTenant() {
        // WHEN
        val url = "http://localhost:$port/v1/transactions/111"
        val ex = assertThrows<HttpClientErrorException> {
            rest.getForEntity(url, GetTransactionResponse::class.java)
        }

        // THEN
        assertEquals(403, ex.rawStatusCode)

        val response = objectMapper.readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.INVALID_TENANT.urn, response.error.code)
    }
}
