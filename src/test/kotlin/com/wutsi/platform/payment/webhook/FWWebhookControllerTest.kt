package com.wutsi.platform.payment.webhook

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.event.TransactionEventPayload
import com.wutsi.platform.payment.provider.flutterwave.model.FWResponseData
import com.wutsi.platform.payment.provider.flutterwave.model.FWWebhookRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.RestTemplate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
internal class FWWebhookControllerTest : ClientHttpRequestInterceptor {
    @LocalServerPort
    public val port: Int = 0

    @MockBean
    private lateinit var eventStream: EventStream

    @Value("\${wutsi.platform.payment.flutterwave.secret-hash}")
    private lateinit var secretHash: String

    private lateinit var url: String

    private val rest = RestTemplate()

    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution
    ): ClientHttpResponse {
        request.headers.add("verif-hash", secretHash)
        return execution.execute(request, body)
    }

    @BeforeEach
    fun setUp() {
        url = "http://localhost:$port/webhooks/flutterwave"

        rest.interceptors.add(this)
    }

    @Test
    fun processEvent() {
        val request = FWWebhookRequest(
            event = "payment.completed",
            FWResponseData(
                id = 1203920932,
                tx_ref = "-transaction-id-"
            )
        )
        rest.postForEntity(url, request, Any::class.java)

        verify(eventStream).enqueue(
            EventURN.TRANSACTION_SYNC_REQUESTED.urn,
            TransactionEventPayload(transactionId = request.data.tx_ref!!)
        )
    }

    @Test
    fun invalidHash() {
        val request = FWWebhookRequest(
            event = "payment.completed",
            FWResponseData(
                id = 1203920932,
                tx_ref = "-transaction-id-"
            )
        )
        RestTemplate().postForEntity(url, request, Any::class.java)

        verify(eventStream, never()).enqueue(any(), any())
    }

    @Test
    fun invalidEvent() {
        val request = FWWebhookRequest(
            event = "payment.completed",
            FWResponseData(
                id = 1203920932,
                tx_ref = null
            )
        )
        rest.postForEntity(url, request, Any::class.java)

        verify(eventStream, never()).enqueue(any(), any())
    }
}
