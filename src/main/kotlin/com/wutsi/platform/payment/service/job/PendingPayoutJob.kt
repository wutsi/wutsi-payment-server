package com.wutsi.platform.payment.service.job

import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.core.Status.PENDING
import com.wutsi.platform.payment.dao.PayoutRepository
import com.wutsi.platform.payment.service.event.EventURN
import com.wutsi.platform.payment.service.event.PayoutEventPayload
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class PendingPayoutJob(
    private val payoutDao: PayoutRepository,
    private val eventStream: EventStream,
    @Value("\${wutsi.application.jobs.process-pending-payouts.enabled}") private val enabled: Boolean
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(PendingPayoutJob::class.java)
    }

    @Scheduled(cron = "\${wutsi.application.jobs.process-pending-payouts.cron}")
    fun run() {
        if (!enabled) {
            LOGGER.info("The job is disabled")
            return
        }

        val payouts = payoutDao.findByStatus(PENDING)
        LOGGER.info("${payouts.size} Payout(s) to process")
        payouts.forEach {
            eventStream.enqueue(EventURN.PAYOUT_PENDING.urn, PayoutEventPayload(it.id))
        }
    }
}
