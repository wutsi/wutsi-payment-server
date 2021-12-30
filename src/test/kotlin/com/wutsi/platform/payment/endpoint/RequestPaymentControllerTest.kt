package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.dao.RequestPaymentRepository
import com.wutsi.platform.payment.dto.RequestPaymentRequest
import com.wutsi.platform.payment.dto.RequestPaymentResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.jdbc.Sql
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql"])
public class RequestPaymentControllerTest : AbstractSecuredController() {
    @LocalServerPort
    public val port: Int = 0

    @Autowired
    private lateinit var dao: RequestPaymentRepository

    private lateinit var url: String

    @BeforeEach
    override fun setUp() {
        super.setUp()
        url = "http://localhost:$port/v1/payments/request"
    }

    @Test
    public fun requestWithNoTTL() {
        // WHEN
        val request = RequestPaymentRequest(
            amount = 50000.0,
            currency = "XAF",
            description = "Yo man",
            invoiceId = UUID.randomUUID().toString(),
            timeToLive = 300
        )
        val response = rest.postForEntity(url, request, RequestPaymentResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        val id = response.body!!.id
        val req = dao.findById(id).get()
        assertEquals(request.amount, req.amount)
        assertEquals(request.currency, req.currency)
        assertEquals(request.description, req.description)
        assertEquals(request.invoiceId, req.invoiceId)
        assertNotNull(req.created)
        assertNotNull(req.expires)
        assertEquals(req.created, req.expires!!.minusSeconds(request.timeToLive!!.toLong()))
    }

    @Test
    public fun requestWithTTL() {
        // WHEN
        val request = RequestPaymentRequest(
            amount = 50000.0,
            currency = "XAF",
            description = "Yo man",
            invoiceId = UUID.randomUUID().toString(),
            timeToLive = null
        )
        val response = rest.postForEntity(url, request, RequestPaymentResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        val id = response.body!!.id
        val req = dao.findById(id).get()
        assertEquals(request.amount, req.amount)
        assertEquals(request.currency, req.currency)
        assertEquals(request.description, req.description)
        assertEquals(request.invoiceId, req.invoiceId)
        assertNotNull(req.created)
        assertNull(req.expires)
    }
}
