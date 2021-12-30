package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.payment.dao.RequestPaymentRepository
import com.wutsi.platform.payment.dto.RequestPaymentRequest
import com.wutsi.platform.payment.dto.RequestPaymentResponse
import com.wutsi.platform.payment.entity.RequestPaymentEntity
import com.wutsi.platform.payment.service.TenantProvider
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

@Service
public class RequestPaymentDelegate(
    private val dao: RequestPaymentRepository,
    private val tenantProvider: TenantProvider,
) : AbstractDelegate() {
    public fun invoke(request: RequestPaymentRequest): RequestPaymentResponse {
        val tenant = tenantProvider.get()
        validateRequest(request, tenant)

        val now = OffsetDateTime.now()
        val obj = dao.save(
            RequestPaymentEntity(
                id = UUID.randomUUID().toString(),
                accountId = securityManager.currentUserId(),
                tenantId = tenantProvider.id(),
                amount = request.amount,
                currency = request.currency,
                description = request.description,
                invoiceId = request.invoiceId,
                created = now,
                expires = request.timeToLive?.let { now.plusSeconds(it.toLong()) }
            )
        )
        return RequestPaymentResponse(
            id = obj.id!!
        )
    }

    private fun validateRequest(request: RequestPaymentRequest, tenant: Tenant) {
        validateCurrency(request.currency, tenant)
    }
}
