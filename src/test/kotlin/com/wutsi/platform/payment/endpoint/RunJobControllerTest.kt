package com.wutsi.platform.payment.endpoint

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.platform.core.error.ErrorResponse
import com.wutsi.platform.payment.dto.RunJobResponse
import com.wutsi.platform.payment.error.ErrorURN
import com.wutsi.platform.payment.job.PendingCashinJob
import com.wutsi.platform.payment.job.PendingCashoutJob
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.web.client.HttpClientErrorException

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RunJobControllerTest : AbstractSecuredController() {
    @LocalServerPort
    public val port: Int = 0

    @MockBean
    private lateinit var pendingCashinJob: PendingCashinJob

    @MockBean
    private lateinit var pendingCashoutJob: PendingCashoutJob

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    public fun pendingCashin() {
        // WHEN
        val url = "http://localhost:$port/v1/jobs/pending-cashin"
        val response = rest.postForEntity(url, null, RunJobResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        verify(pendingCashinJob).run()
    }

    @Test
    public fun pendingCashout() {
        // WHEN
        val url = "http://localhost:$port/v1/jobs/pending-cashout"
        val response = rest.postForEntity(url, null, RunJobResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        verify(pendingCashoutJob).run()
    }

    @Test
    public fun badJob() {
        // WHEN
        val url = "http://localhost:$port/v1/jobs/xxx"
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, null, RunJobResponse::class.java)
        }

        // THEN
        assertEquals(400, ex.rawStatusCode)

        val response = objectMapper.readValue(ex.responseBodyAsString, ErrorResponse::class.java)
        assertEquals(ErrorURN.INVALID_JOB.urn, response.error.code)
    }

    @Test
    public fun badPermission() {
        // GIVEN
        rest = createResTemplate(scope = listOf("payment-read", "payment-manage"))

        // WHEN
        val url = "http://localhost:$port/v1/jobs/xxx"
        val ex = assertThrows<HttpClientErrorException> {
            rest.postForEntity(url, null, RunJobResponse::class.java)
        }

        // THEN
        assertEquals(403, ex.rawStatusCode)
    }
}
