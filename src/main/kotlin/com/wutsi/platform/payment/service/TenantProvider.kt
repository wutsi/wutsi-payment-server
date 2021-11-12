package com.wutsi.platform.payment.service

import com.wutsi.platform.core.tracing.TracingContext
import com.wutsi.platform.tenant.WutsiTenantApi
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.stereotype.Service

@Service
public class TenantProvider(
    private val tenantApi: WutsiTenantApi,
    private val tracingContext: TracingContext
) {
    fun get(): Tenant {
        val tenantId = tracingContext.tenantId()!!.toLong()
        return tenantApi.getTenant(tenantId).tenant
    }
}
