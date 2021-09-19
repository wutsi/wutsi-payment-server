package com.wutsi.platform.payment.service.job

import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.core.Status.PENDING
import com.wutsi.platform.payment.dao.ChargeRepository
import com.wutsi.platform.payment.service.event.ChargeEventPayload
import com.wutsi.platform.payment.service.event.EventURN
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class PendingChargesJob(
    private val chargeDao: ChargeRepository,
    private val eventStream: EventStream,
    @Value("\${wutsi.application.jobs.process-pending-charges.enabled}") private val enabled: Boolean
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(PendingChargesJob::class.java)
    }

    @Scheduled(cron = "\${wutsi.application.jobs.process-pending-charges.cron}")
    fun run() {
        if (!enabled) {
            LOGGER.info("The job is disabled")
            return
        }

        val charges = chargeDao.findByStatus(PENDING)
        LOGGER.info("${charges.size} Charge(s) to process")
        charges.forEach {
            eventStream.enqueue(EventURN.CHARGE_PENDING.urn, ChargeEventPayload(it.id))
        }
    }
}
