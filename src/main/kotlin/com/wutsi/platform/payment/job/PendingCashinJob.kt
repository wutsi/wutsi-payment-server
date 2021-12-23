package com.wutsi.platform.payment.job

import com.wutsi.platform.core.logging.DefaultKVLogger
import com.wutsi.platform.payment.GatewayProvider
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.delegate.CreateCashinDelegate
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.model.CreatePaymentResponse
import com.wutsi.platform.payment.service.TenantProvider
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class PendingCashinJob(
    private val dao: TransactionRepository,
    private val delegate: CreateCashinDelegate,
    private val gatewayProvider: GatewayProvider,
    tenantProvider: TenantProvider
) : AbstractTransactionJob(tenantProvider) {

    @Scheduled(cron = "\${wutsi.application.jobs.pending-cashin.cron}")
    override fun run() {
        super.run()
    }

    override fun getJobName(): String =
        "pending-cashin"

    override fun doRun(): Long {
        var count = 0L
        val size = 100
        var page = 0
        while (true) {
            val pagination = PageRequest.of(page, size)
            val txs = dao.findByTypeAndStatus(TransactionType.CASHIN, Status.PENDING, pagination)
            txs.forEach {
                val tc = initTracingContext(it)
                try {
                    onCashin(it)
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

    private fun onCashin(tx: TransactionEntity) {
        if (tx.paymentMethodProvider == null)
            return

        val gateway = gatewayProvider.get(tx.paymentMethodProvider)
        val tenants: MutableMap<Long, Tenant> = mutableMapOf()

        val logger = DefaultKVLogger()
        logger.add("transaction_id", tx.id)
        logger.add("provider", tx.paymentMethodProvider)
        logger.add("job", getJobName())
        try {
            val response = gateway.getPayment(tx.id!!)
            logger.add("status", response.status)
            logger.add("financial_transaction_id", response.financialTransactionId)

            if (response.status == Status.SUCCESSFUL) {
                val tenant = findTenant(tx.tenantId, tenants)
                delegate.onSuccess(
                    tx = tx,
                    tenant = tenant,
                    response = CreatePaymentResponse(
                        transactionId = tx.id,
                        financialTransactionId = response.financialTransactionId,
                        status = response.status
                    )
                )
            }
        } catch (ex: PaymentException) {
            logger.add("status", Status.FAILED)
            logger.add("error_code", ex.error.code)
            logger.add("error_supplier_error_code", ex.error.supplierErrorCode)

            delegate.onError(tx, ex)
        } finally {
            logger.log()
        }
    }
}
