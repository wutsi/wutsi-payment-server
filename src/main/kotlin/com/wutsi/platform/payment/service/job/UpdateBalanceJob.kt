package com.wutsi.platform.payment.service.job

import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.service.event.EventURN
import com.wutsi.platform.payment.service.event.UpdateBalanceEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import javax.persistence.EntityManager

@Service
class UpdateBalanceJob(
    private val eventStream: EventStream,
    private val em: EntityManager,
    @Value("\${wutsi.application.jobs.update-balances.enabled}") private val enabled: Boolean
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(UpdateBalanceJob::class.java)
    }

    @Scheduled(cron = "\${wutsi.application.jobs.update-balances.cron}")
    fun run() {
        if (!enabled) {
            LOGGER.info("The job is disabled")
            return
        }

        LOGGER.info("Updating account balances")
        val sql = """
            SELECT DISTINCT T.account_id, T.payment_method_provider
            FROM
                T_TRANSACTION T LEFT JOIN T_BALANCE B ON T.account_id=B.account_id
            WHERE
                T.created>=B.synced OR B.synced IS NULL
        """.trimIndent()

        val query = em.createNativeQuery(sql)
        val result = query.resultList

        LOGGER.info("${result.size} account(s) to update")
        (result as List<Array<Any>>).forEach {
            eventStream.enqueue(
                EventURN.BALANCE_UPDATE_REQUESTED.urn,
                UpdateBalanceEvent(
                    accountId = (it[0] as Number).toLong(),
                    paymentMethodProvider = PaymentMethodProvider.values()[(it[1] as Number).toInt()]
                )
            )
        }
    }
}
