package com.wutsi.platform.payment.job

import com.wutsi.platform.core.cron.AbstractCronJob
import com.wutsi.platform.core.security.spring.ApplicationTokenProvider
import com.wutsi.platform.payment.service.TenantProvider
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractTransactionJob : AbstractCronJob() {
    @Autowired
    protected lateinit var tenantProvider: TenantProvider

    @Autowired
    protected lateinit var applicationTokenProvider: ApplicationTokenProvider

    protected fun findTenant(id: Long, tenants: MutableMap<Long, Tenant>): Tenant {
        var tenant = tenants[id]
        if (tenant != null)
            return tenant

        tenant = tenantProvider.get(id)
        tenants[id] = tenant
        return tenant
    }
}
