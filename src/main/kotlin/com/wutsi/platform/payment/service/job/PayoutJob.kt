package com.wutsi.platform.payment.service.job

import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.service.BalanceService
import com.wutsi.platform.payment.service.event.EventURN
import com.wutsi.platform.payment.service.event.PayoutRequestedEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class PayoutJob(
    private val balanceService: BalanceService,
    private val eventStream: EventStream,
    @Value("\${wutsi.application.jobs.payouts.enabled}") private val enabled: Boolean
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(PayoutJob::class.java)
    }

    @Scheduled(cron = "\${wutsi.application.jobs.payouts.cron}")
    fun run() {
        if (!enabled) {
            LOGGER.info("The job is disabled")
            return
        }

        val balances = balanceService.getBalanceToPayout()
        LOGGER.info("${balances.size} balance(s) to payout")
        balances.forEach {
            eventStream.enqueue(
                EventURN.PAYOUT_REQUESTED.urn,
                PayoutRequestedEvent(it.accountId)
            )
        }
    }
}
