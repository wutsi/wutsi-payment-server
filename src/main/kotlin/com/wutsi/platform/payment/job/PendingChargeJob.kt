package com.wutsi.platform.payment.job

import com.wutsi.platform.core.logging.KVLogger
import com.wutsi.platform.payment.Gateway
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.delegate.AbstractDelegate
import com.wutsi.platform.payment.delegate.CreateChargeDelegate
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.model.CreatePaymentResponse
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class PendingChargeJob(
    private val delegate: CreateChargeDelegate,
) : AbstractPendingTransactionJob() {

    @Scheduled(cron = "\${wutsi.application.jobs.pending-charge.cron}")
    override fun run() {
        super.run()
    }

    override fun getJobName() = "pending-charge"

    override fun getTransactionType() = TransactionType.CHARGE

    override fun getDelegate(): AbstractDelegate = delegate

    override fun doProcess(tx: TransactionEntity, tenant: Tenant, gateway: Gateway, logger: KVLogger) {
        val response = gateway.getPayment(tx.gatewayTransactionId!!)
        logger.add("transaction_status", response.status)

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
