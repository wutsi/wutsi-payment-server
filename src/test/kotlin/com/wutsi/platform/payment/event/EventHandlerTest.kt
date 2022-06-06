package com.wutsi.platform.payment.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.platform.core.stream.Event
import com.wutsi.platform.core.stream.EventTracingData
import com.wutsi.platform.payment.endpoint.AbstractSecuredController
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.OffsetDateTime
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
internal class EventHandlerTest : AbstractSecuredController() {
    @MockBean
    private lateinit var transactionEventHandler: TransactionEventHandler

    @Autowired
    private lateinit var eventHandler: EventHandler

    @BeforeEach
    override fun setUp() {
        super.setUp()
    }

    @Test
    private fun onSyncRequested() {
        val event = createEvent("111", EventURN.TRANSACTION_SYNC_REQUESTED)
        eventHandler.onEvent(event)

        verify(transactionEventHandler).onSync("111")
    }

    private fun createEvent(
        transactionId: String,
        type: EventURN,
    ): Event =
        Event(
            id = UUID.randomUUID().toString(),
            type = type.urn,
            timestamp = OffsetDateTime.now(),
            tracingData = EventTracingData(
                tenantId = null,
                traceId = UUID.randomUUID().toString(),
                clientId = "-",
                clientInfo = "--",
                deviceId = "---"
            ),
            payload = ObjectMapper().writeValueAsString(
                TransactionEventPayload(
                    transactionId = transactionId
                )
            )
        )
}
