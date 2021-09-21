package com.wutsi.platform.payment.service.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.wutsi.platform.core.stream.Event
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.exception.ChargeException
import com.wutsi.platform.payment.exception.PayoutException
import com.wutsi.platform.payment.service.BalanceService
import com.wutsi.platform.payment.service.ChargeService
import com.wutsi.platform.payment.service.PayoutService
import com.wutsi.platform.payment.service.TransactionService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class EventHandler(
    private val transactions: TransactionService,
    private val balances: BalanceService,
    private val payouts: PayoutService,
    private val charges: ChargeService,
    private val mapper: ObjectMapper,
    private val eventStream: EventStream
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(EventHandler::class.java)
    }

    @EventListener
    fun onEvent(event: Event) {
        LOGGER.info("onEvent(${event.type}, ...)")

        when (event.type) {
            EventURN.CHARGE_PENDING.urn -> chargePending(event)

            EventURN.CHARGE_SUCCESSFUL.urn -> recordChargeTransaction(event)

            EventURN.CHARGE_FAILED.urn -> notifyChargeFailure(event)

            EventURN.BALANCE_UPDATE_REQUESTED.urn -> updateBalance(event)

            EventURN.PAYOUT_SUCCESSFUL.urn -> recordPayoutTransaction(event)

            EventURN.PAYOUT_PENDING.urn -> syncPayout(event)

            EventURN.PAYOUT_FAILED.urn -> notifyPayoutFailure(event)

            else -> LOGGER.info("Ignoring event: ${event.type}")
        }
    }

    private fun chargePending(event: Event) {
        try {
            val payload = asChargeEventPayload(event)
            LOGGER.info("Charge - Synchronizing. chargeId=${payload.chargeId}")
            charges.sync(payload.chargeId)
        } catch (ex: ChargeException) {
            LOGGER.warn("Charge error", ex)
        }
    }

    private fun recordChargeTransaction(event: Event) {
        val payload = asChargeEventPayload(event)
        LOGGER.info("Charge - Recording Transaction. chargeId=${payload.chargeId}")
        transactions.onChargeSuccessful(payload.chargeId)
    }

    private fun updateBalance(event: Event) {
        val payload = asBalanceEventPayload(event)
        LOGGER.info("Balance - Updating. accountId=${payload.accountId} paymentMethodProvider=${payload.paymentMethodProvider}")
        balances.update(payload.accountId, payload.paymentMethodProvider)
    }

    private fun notifyChargeFailure(event: Event) {
        val payload = asChargeEventPayload(event)
        LOGGER.info("Charge - FAILURE. payoutId=${payload.chargeId}")
        eventStream.publish(event.type, payload)
    }

    private fun syncPayout(event: Event) {
        val payload = asPayoutEventPayload(event)
        LOGGER.info("Payout - Synchronizing. payoutId=${payload.payoutId}")
        try {
            payouts.sync(payload.payoutId)
        } catch (ex: PayoutException) {
            LOGGER.warn("Payout synchronization error", ex)
        }
    }

    private fun recordPayoutTransaction(event: Event) {
        val payload = asPayoutEventPayload(event)
        LOGGER.info("Payout - Recording Transaction. payoutId=${payload.payoutId}")
        transactions.onPayoutSuccessful(payload.payoutId)
    }

    private fun notifyPayoutFailure(event: Event) {
        val payload = asPayoutEventPayload(event)
        LOGGER.info("Payout - FAILURE. payoutId=${payload.payoutId}")
        eventStream.publish(event.type, payload)
    }

    private fun asChargeEventPayload(event: Event): ChargeEventPayload =
        mapper.readValue(event.payload, ChargeEventPayload::class.java)

    private fun asBalanceEventPayload(event: Event): BalanceEventPayload =
        mapper.readValue(event.payload, BalanceEventPayload::class.java)

    private fun asPayoutEventPayload(event: Event): PayoutEventPayload =
        mapper.readValue(event.payload, PayoutEventPayload::class.java)
}
