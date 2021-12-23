package com.wutsi.platform.payment.job

import com.wutsi.platform.core.logging.DefaultKVLogger
import com.wutsi.platform.payment.GatewayProvider
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.delegate.CreateCashoutDelegate
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.model.CreateTransferResponse
import com.wutsi.platform.payment.service.TenantProvider
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class PendingCashoutJob(
    private val dao: TransactionRepository,
    private val delegate: CreateCashoutDelegate,
    private val gatewayProvider: GatewayProvider,
    tenantProvider: TenantProvider
) : AbstractTransactionJob(tenantProvider) {
    @Scheduled(cron = "\${wutsi.application.jobs.pending-cashin.cron}")
    override fun run() {
        super.run()
    }

    override fun getJobName(): String =
        "pending-cashout"

    override fun doRun(): Long {
        var count = 0L
        val size = 100
        var page = 0
        while (true) {
            val pagination = PageRequest.of(page, size)
            val txs = dao.findByTypeAndStatus(TransactionType.CASHOUT, Status.PENDING, pagination)
            txs.forEach {
                val tc = initTracingContext(it)
                try {
                    onCashout(it)
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

    private fun onCashout(tx: TransactionEntity) {
        val logger = DefaultKVLogger()
        logger.add("transaction_id", tx.id)
        logger.add("provider", tx.paymentMethodProvider)
        logger.add("job", getJobName())

        if (tx.paymentMethodProvider == null)
            return

        val gateway = gatewayProvider.get(tx.paymentMethodProvider)
        val tenants: MutableMap<Long, Tenant> = mutableMapOf()
        val tenant = findTenant(tx.tenantId, tenants)
        try {
            val response = gateway.getTransfer(tx.id!!)
            logger.add("status", response.status)
            logger.add("financial_transaction_id", response.financialTransactionId)

            if (response.status == Status.SUCCESSFUL) {
                delegate.onSuccess(
                    tx = tx,
                    tenant = tenant,
                    response = CreateTransferResponse(
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

            delegate.onError(tx, ex, tenant)
        } finally {
            logger.log()
        }
    }
}
