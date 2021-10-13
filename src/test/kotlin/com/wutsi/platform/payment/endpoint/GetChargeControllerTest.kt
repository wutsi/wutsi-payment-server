package com.wutsi.platform.payment.endpoint

import com.fasterxml.jackson.databind.ObjectMapper
import com.wutsi.platform.core.error.ErrorResponse
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodType
import com.wutsi.platform.payment.core.ErrorCode
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dto.GetChargeResponse
import com.wutsi.platform.payment.util.ErrorURN
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.jdbc.Sql
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/GetChargeController.sql"])
public class GetChargeControllerTest : AbstractSecuredController() {
    @LocalServerPort
    public val port: Int = 0

    private lateinit var url: String
    private lateinit var rest: RestTemplate

    @BeforeEach
    override fun setUp() {
        super.setUp()

        rest = createResTemplate(listOf("payment-read"), 1)
    }

    @Test
    public fun `get charge`() {
        url = "http://localhost:$port/v1/charges/1111"
        val response = rest.getForEntity(url, GetChargeResponse::class.java)

        assertEquals(200, response.statusCodeValue)

        val charge = response.body.charge
        assertEquals(1L, charge.merchantId)
        assertEquals(11L, charge.customerId)
        assertEquals(111L, charge.userId)
        assertEquals(1111L, charge.applicationId)
        assertEquals("1111-token", charge.paymentMethodToken)
        assertEquals(PaymentMethodProvider.MTN.name, charge.paymentMethodProvider)
        assertEquals(PaymentMethodType.MOBILE.name, charge.paymentMethodType)
        assertEquals("urn:order:1111", charge.externalId)
        assertEquals(10000.0, charge.amount)
        assertEquals("XAF", charge.currency)
        assertEquals(Status.FAILED.name, charge.status)
        assertEquals("1111-0000", charge.gatewayTransactionId)
        assertEquals("2222-0000", charge.financialTransactionId)
        assertEquals(ErrorCode.AUTHENTICATION_FAILED.name, charge.errorCode)
        assertEquals("FAILURE", charge.supplierErrorCode)
        assertEquals("Sample charge", charge.description)
    }

    @Test
    public fun `get charge with invalid ID`() {
        url = "http://localhost:$port/v1/charges/999999"
        val ex = assertThrows<HttpClientErrorException> {
            rest.getForEntity(url, GetChargeResponse::class.java)
        }

        assertEquals(404, ex.rawStatusCode)

        val response = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.CHARGE_NOT_FOUND.urn, response.error.code)
    }

    @Test
    fun `anonymous cannot gt charges`() {
        rest = RestTemplate()

        url = "http://localhost:$port/v1/charges/1111"
        val ex = assertThrows<HttpClientErrorException> {
            rest.getForEntity(url, GetChargeResponse::class.java)
        }

        assertEquals(401, ex.rawStatusCode)
    }

    @Test
    fun `user with invalid permission cannot create charges`() {
        rest = createResTemplate(listOf("xxx"), 1)

        url = "http://localhost:$port/v1/charges/1111"
        val ex = assertThrows<HttpClientErrorException> {
            rest.getForEntity(url, GetChargeResponse::class.java)
        }

        assertEquals(403, ex.rawStatusCode)
    }
}
