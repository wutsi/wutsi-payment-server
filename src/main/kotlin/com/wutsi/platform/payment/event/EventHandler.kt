package com.wutsi.platform.payment.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.wutsi.platform.core.stream.Event
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class EventHandler(
    private val objectMapper: ObjectMapper,
    private val transactionEventHandler: TransactionEventHandler,
) {
    @EventListener
    fun onEvent(event: Event) {
        if (EventURN.TRANSACTION_SYNC_REQUESTED.urn == event.type) {
            val payload = objectMapper.readValue(event.payload, TransactionEventPayload::class.java)
            transactionEventHandler.onSync(payload.transactionId)
        }
    }
}
