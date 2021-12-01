package com.wutsi.platform.payment.job

import com.wutsi.platform.core.logging.RequestKVLogger
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
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class PendingCashinJob(
    private val dao: TransactionRepository,
    private val delegate: CreateCashinDelegate,
    private val gatewayProvider: GatewayProvider,
    private val tenantProvider: TenantProvider
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(PendingCashinJob::class.java)
    }

    @Scheduled(cron = "\${wutsi.application.jobs.pending-cashin.cron}")
    fun run() {
        val size = 100
        var page = 0
        while (true) {
            val pagination = PageRequest.of(page, size)
            val txs = dao.findByTypeAndStatus(TransactionType.CASHIN, Status.PENDING, pagination)

            LOGGER.info("${txs.size} Pending Transactions")
            txs.forEach { onCashin(it) }

            if (txs.isEmpty())
                break
            else
                page += size
        }
    }

    private fun onCashin(tx: TransactionEntity) {
        val logger = RequestKVLogger()
        logger.add("transaction_id", tx.id)
        logger.add("provider", tx.paymentMethodProvider)

        if (tx.paymentMethodProvider == null)
            return

        val gateway = gatewayProvider.get(tx.paymentMethodProvider)
        val tenants: MutableMap<Long, Tenant> = mutableMapOf()
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
        }
    }

    private fun findTenant(id: Long, tenants: MutableMap<Long, Tenant>): Tenant {
        var tenant = tenants[id]
        if (tenant != null)
            return tenant

        tenant = tenantProvider.get(id)
        tenants[id] = tenant
        return tenant
    }
}
