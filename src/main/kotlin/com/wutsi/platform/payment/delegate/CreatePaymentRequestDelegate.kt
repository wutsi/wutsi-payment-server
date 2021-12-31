package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.payment.dao.PaymentRequestRepository
import com.wutsi.platform.payment.dto.CreatePaymentRequestRequest
import com.wutsi.platform.payment.dto.CreatePaymentRequestResponse
import com.wutsi.platform.payment.entity.PaymentRequestEntity
import com.wutsi.platform.payment.service.TenantProvider
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

@Service
public class CreatePaymentRequestDelegate(
    private val dao: PaymentRequestRepository,
    private val tenantProvider: TenantProvider,
) : AbstractDelegate() {
    public fun invoke(request: CreatePaymentRequestRequest): CreatePaymentRequestResponse {
        logger.add("currency", request.currency)
        logger.add("amount", request.amount)
        logger.add("invoice_id", request.invoiceId)
        logger.add("description", request.description)
        logger.add("time_to_live", request.timeToLive)

        val tenant = tenantProvider.get()
        validateRequest(request, tenant)

        val now = OffsetDateTime.now()
        val obj = dao.save(
            PaymentRequestEntity(
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
        return CreatePaymentRequestResponse(
            id = obj.id!!
        )
    }

    private fun validateRequest(request: CreatePaymentRequestRequest, tenant: Tenant) {
        validateCurrency(request.currency, tenant)
        ensureCurrentUserActive()
    }
}
