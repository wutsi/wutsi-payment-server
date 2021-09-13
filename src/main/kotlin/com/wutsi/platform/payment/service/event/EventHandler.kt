package com.wutsi.platform.payment.service.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.wutsi.platform.core.stream.Event
import com.wutsi.platform.payment.service.TransactionService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class EventHandler(
    private val service: TransactionService,
    private val mapper: ObjectMapper
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(EventHandler::class.java)
    }

    @EventListener
    fun onEvent(event: Event) {
        LOGGER.info("onEvent(${event.type}, ...)")

        when (event.type) {
            EventURN.CHARGE_PENDING.urn -> service.onChargePending(asChargeEventPayload(event).chargeId)
            EventURN.CHARGE_SUCCESSFUL.urn -> service.onChargeSuccessful(asChargeEventPayload(event).chargeId)
            else -> LOGGER.info("Ignoring event")
        }
    }

    private fun asChargeEventPayload(event: Event): ChargeEventPayload =
        mapper.readValue(event.payload, ChargeEventPayload::class.java)
}
