package com.wutsi.platform.payment.endpoint

import com.fasterxml.jackson.databind.ObjectMapper
import com.wutsi.platform.core.error.ErrorResponse
import com.wutsi.platform.payment.dto.GetPaymentRequestResponse
import com.wutsi.platform.payment.error.ErrorURN
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.jdbc.Sql
import org.springframework.web.client.HttpClientErrorException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/GetPaymentRequestController.sql"])
public class GetPaymentRequestControllerTest : AbstractSecuredController() {
    @LocalServerPort
    public val port: Int = 0

    @Test
    public fun success() {
        val url = "http://localhost:$port/v1/payment-requests/200"
        val response = rest.getForEntity(url, GetPaymentRequestResponse::class.java)

        assertEquals(200, response.statusCodeValue)

        val req = response.body!!.paymentRequest
        assertEquals(50000.0, req.amount)
        assertEquals("XAF", req.currency)
        assertEquals("This is description", req.description)
        assertEquals("INV-200", req.orderId)
        assertNotNull(req.created)
        assertNotNull(req.expires)
    }

    @Test
    public fun notFound() {
        val url = "http://localhost:$port/v1/payment-requests/9999"
        val ex = assertThrows<HttpClientErrorException> {
            rest.getForEntity(url, GetPaymentRequestResponse::class.java)
        }

        assertEquals(404, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.PAYMENT_REQUEST_NOT_FOUND.urn, response.error.code)
    }

    @Test
    public fun illegalTenant() {
        val url = "http://localhost:$port/v1/payment-requests/777"
        val ex = assertThrows<HttpClientErrorException> {
            rest.getForEntity(url, GetPaymentRequestResponse::class.java)
        }

        assertEquals(403, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.ILLEGAL_TENANT_ACCESS.urn, response.error.code)
    }
}
