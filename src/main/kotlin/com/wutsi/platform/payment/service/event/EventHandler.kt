package com.wutsi.platform.payment.service.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.wutsi.platform.core.stream.Event
import com.wutsi.platform.payment.service.BalanceService
import com.wutsi.platform.payment.service.TransactionService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class EventHandler(
    private val transactions: TransactionService,
    private val balances: BalanceService,
    private val mapper: ObjectMapper
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(EventHandler::class.java)
    }

    @EventListener
    fun onEvent(event: Event) {
        LOGGER.info("onEvent(${event.type}, ...)")

        when (event.type) {
            EventURN.CHARGE_PENDING.urn -> transactions.onChargePending(asChargeEventPayload(event).chargeId)
            EventURN.CHARGE_SUCCESSFUL.urn -> transactions.onChargeSuccessful(asChargeEventPayload(event).chargeId)
            EventURN.BALANCE_UPDATE_REQUESTED.urn -> balances.update(asAccountEventPayload(event).accountId)
            else -> LOGGER.info("Ignoring event: ${event.type}")
        }
    }

    private fun asChargeEventPayload(event: Event): ChargeEventPayload =
        mapper.readValue(event.payload, ChargeEventPayload::class.java)

    private fun asAccountEventPayload(event: Event): AccountEventPayload =
        mapper.readValue(event.payload, AccountEventPayload::class.java)
}
