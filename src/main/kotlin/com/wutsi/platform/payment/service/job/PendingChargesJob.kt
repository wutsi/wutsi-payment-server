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
    @Value("\${wutsi.application.charges.job.process-pending.enabled}") private val enabled: Boolean,
    @Value("\${wutsi.application.charges.job.process-pending.max-duration-millis}") private val maxDurationMillis: Long
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(PendingChargesJob::class.java)
    }

    @Scheduled(cron = "\${wutsi.application.charges.job.process-pending.cron}")
    fun run() {

        if (!enabled) {
            LOGGER.info("The job is disabled")
            return
        }

        LOGGER.info("Processing PENDING charges")
        var count = 0L
        val started = System.currentTimeMillis()
        val charges = chargeDao.findByStatus(PENDING)

        charges.forEach {
            if (System.currentTimeMillis() - started >= maxDurationMillis) {
                LOGGER.info("Timeout. $count charge(s) processed")
                return@forEach
            }

            count++
            eventStream.enqueue(EventURN.CHARGE_PENDING.urn, ChargeEventPayload(it.id))
        }
    }
}
