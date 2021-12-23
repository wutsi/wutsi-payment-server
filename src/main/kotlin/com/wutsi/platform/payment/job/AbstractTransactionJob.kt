package com.wutsi.platform.payment.job

import com.wutsi.platform.core.cron.AbstractCronJob
import com.wutsi.platform.core.security.spring.ApplicationTokenProvider
import com.wutsi.platform.core.tracing.DefaultTracingContext
import com.wutsi.platform.core.tracing.ThreadLocalTracingContextHolder
import com.wutsi.platform.core.tracing.TracingContext
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.service.TenantProvider
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

abstract class AbstractTransactionJob : AbstractCronJob() {
    @Autowired
    protected lateinit var tenantProvider: TenantProvider

    @Autowired
    protected lateinit var applicationTokenProvider: ApplicationTokenProvider

    override fun getToken(): String? =
        applicationTokenProvider.getToken()

    protected fun initTracingContext(tx: TransactionEntity): TracingContext? {
        val tc = ThreadLocalTracingContextHolder.get()
        ThreadLocalTracingContextHolder.set(
            DefaultTracingContext(
                tenantId = tx.tenantId.toString(),
                traceId = tc?.traceId() ?: UUID.randomUUID().toString(),
                deviceId = tc?.deviceId() ?: getJobName(),
                clientId = tc?.clientId() ?: getJobName(),
            )
        )
        return tc
    }

    protected fun restoreTracingContext(tc: TracingContext?) {
        if (tc == null)
            ThreadLocalTracingContextHolder.remove()
        else
            ThreadLocalTracingContextHolder.set(tc)
    }

    protected fun findTenant(id: Long, tenants: MutableMap<Long, Tenant>): Tenant {
        var tenant = tenants[id]
        if (tenant != null)
            return tenant

        tenant = tenantProvider.get(id)
        tenants[id] = tenant
        return tenant
    }
}
