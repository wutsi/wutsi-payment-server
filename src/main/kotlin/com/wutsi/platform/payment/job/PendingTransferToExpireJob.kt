package com.wutsi.platform.payment.job

import com.wutsi.platform.core.logging.DefaultKVLogger
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.core.Error
import com.wutsi.platform.payment.core.ErrorCode
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.delegate.CreateTransferDelegate
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class PendingTransferToExpireJob(
    private val dao: TransactionRepository,
    private val delegate: CreateTransferDelegate,
) : AbstractTransactionJob() {
    @Scheduled(cron = "\${wutsi.application.jobs.pending-transfer-to-expire.cron}")
    override fun run() {
        super.run()
    }

    override fun getJobName(): String =
        "pending-transfer-to-expire"

    override fun doRun(): Long {
        var count = 0L
        val size = 100
        var page = 0
        val now = OffsetDateTime.now()
        while (true) {
            val pagination = PageRequest.of(page, size)
            val txs =
                dao.findByTypeAndStatusAndExpiresLessThan(TransactionType.TRANSFER, Status.PENDING, now, pagination)
            txs.forEach {
                val tc = initTracingContext(it)
                try {
                    onExpire(it)
                    count++
                } finally {
                    restoreTracingContext(tc)
                }
            }

            if (txs.isEmpty())
                break
            else
                page += size
        }
        return count
    }

    private fun onExpire(tx: TransactionEntity) {
        val logger = DefaultKVLogger()
        logger.add("transaction_id", tx.id)
        logger.add("job", getJobName())

        val ex = PaymentException(
            error = Error(
                code = ErrorCode.EXPIRED
            )
        )
        try {
            delegate.onError(tx, ex)
            logger.add("status", tx.status)
        } finally {
            logger.log()
        }
    }
}
