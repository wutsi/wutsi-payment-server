package com.wutsi.platform.payment.webhook

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.event.TransactionEventPayload
import com.wutsi.platform.payment.provider.flutterwave.model.FWResponseData
import com.wutsi.platform.payment.provider.flutterwave.model.FWWebhookRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class FWWebhookControllerTest {
    private lateinit var eventStream: EventStream
    private val secretHash = "111"
    private lateinit var controller: FWWebhookController

    @BeforeEach
    fun setUp() {
        eventStream = mock()
        controller = FWWebhookController(
            logger = mock(),
            eventStream = eventStream,
            secretHash = secretHash
        )
    }

    fun processEvent() {
        val request = FWWebhookRequest(
            event = "payment.completed",
            FWResponseData(
                id = 1203920932,
                tx_ref = "-transaction-id-"
            )
        )
        controller.invoke(request, secretHash)

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
        controller.invoke(request, "????")

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
        controller.invoke(request, secretHash)

        verify(eventStream, never()).enqueue(any(), any())
    }
}
