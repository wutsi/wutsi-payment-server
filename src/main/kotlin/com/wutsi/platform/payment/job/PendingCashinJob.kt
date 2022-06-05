package com.wutsi.platform.payment.job

import com.wutsi.platform.core.logging.KVLogger
import com.wutsi.platform.payment.Gateway
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.delegate.AbstractDelegate
import com.wutsi.platform.payment.delegate.CreateCashinDelegate
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.model.CreatePaymentResponse
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class PendingCashinJob(
    private val delegate: CreateCashinDelegate,
) : AbstractPendingTransactionJob() {

    @Scheduled(cron = "\${wutsi.application.jobs.pending-cashin.cron}")
    override fun run() {
        super.run()
    }

    override fun getJobName() = "pending-cashin"

    override fun getTransactionType() = TransactionType.CASHIN

    override fun getDelegate(): AbstractDelegate = delegate

    override fun doProcess(tx: TransactionEntity, tenant: Tenant, gateway: Gateway, logger: KVLogger) {
        val response = gateway.getPayment(tx.gatewayTransactionId!!)
        logger.add("status", response.status)

        if (response.status == Status.SUCCESSFUL) {
            delegate.onSuccess(
                tx = tx,
                tenant = tenant,
                response = CreatePaymentResponse(
                    transactionId = tx.id!!,
                    financialTransactionId = response.financialTransactionId,
                    status = response.status
                )
            )
        }
    }
}
