package com.wutsi.platform.payment.job

import com.wutsi.platform.core.logging.DefaultKVLogger
import com.wutsi.platform.core.logging.KVLogger
import com.wutsi.platform.payment.Gateway
import com.wutsi.platform.payment.GatewayProvider
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.delegate.AbstractDelegate
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest

abstract class AbstractPendingTransactionJob : AbstractTransactionJob() {
    @Autowired
    protected lateinit var dao: TransactionRepository

    @Autowired
    private lateinit var gatewayProvider: GatewayProvider

    protected abstract fun getTransactionType(): TransactionType

    protected abstract fun getDelegate(): AbstractDelegate

    @kotlin.jvm.Throws(PaymentException::class)
    protected abstract fun doProcess(tx: TransactionEntity, tenant: Tenant, gateway: Gateway, logger: KVLogger)

    override fun doRun(): Long {
        var count = 0L
        val size = 100
        var page = 0
        val tenants: MutableMap<Long, Tenant> = mutableMapOf()
        while (true) {
            val pagination = PageRequest.of(page, size)
            val txs = dao.findByTypeAndStatus(getTransactionType(), Status.PENDING, pagination)
            txs.forEach {
                doProcess(it, tenants)
                count++
            }

            if (txs.isEmpty())
                break
            else
                page += size
        }
        return count
    }

    private fun doProcess(tx: TransactionEntity, tenants: MutableMap<Long, Tenant>) {
        val gateway = tx.paymentMethodProvider?.let { gatewayProvider.get(it) }
            ?: return

        val tenant = findTenant(tx.tenantId, tenants)

        val logger = DefaultKVLogger()
        logger.add("transaction_id", tx.id)
        logger.add("provider", tx.paymentMethodProvider)
        logger.add("transaction_gateway_id", tx.gatewayTransactionId)
        logger.add("gateway", gateway::class.java.simpleName)
        try {
            doProcess(tx, tenant, gateway, logger)
        } catch (ex: PaymentException) {
            logger.add("status", Status.FAILED)
            logger.add("error_code", ex.error.code)
            logger.add("error_supplier_error_code", ex.error.supplierErrorCode)

            getDelegate().onError(tx, ex)
        } finally {
            logger.log()
        }
    }
}
