package com.wutsi.platform.payment.job

import com.wutsi.platform.core.cron.AbstractCronJob
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.event.TransactionEventPayload
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class PendingTransactionJob(
    private val dao: TransactionRepository,
    private val eventStream: EventStream,
) : AbstractCronJob() {
    override fun getJobName() = "pending-transaction"

    @Scheduled(cron = "\${wutsi.application.jobs.pending-transaction.cron}")
    override fun run() {
        super.run()
    }

    override fun doRun(): Long {
        var count = 0L
        val size = 100
        var page = 0
        while (true) {
            val pagination = PageRequest.of(page, size)
            val txs = dao.findByStatus(Status.PENDING, pagination)
            txs.forEach {
                eventStream.enqueue(
                    type = EventURN.TRANSACTION_SYNC_REQUESTED.urn,
                    payload = TransactionEventPayload(transactionId = it.id!!, type = it.type.name)
                )
                count++
            }

            if (txs.size < size)
                break
            else
                page += size
        }
        return count
    }
}
