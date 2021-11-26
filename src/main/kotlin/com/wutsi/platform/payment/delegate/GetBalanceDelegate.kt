package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.payment.dto.GetBalanceResponse
import com.wutsi.platform.payment.entity.BalanceEntity
import com.wutsi.platform.payment.service.TenantProvider
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class GetBalanceDelegate(
    private val tenantProvider: TenantProvider,
) : AbstractDelegate() {
    fun invoke(userId: Long): GetBalanceResponse {
        val tenant = tenantProvider.get()
        val balance = balanceDao.findByUserIdAndTenantId(userId, tenant.id)
            .orElseGet {
                BalanceEntity(
                    userId = userId,
                    amount = 0.0,
                    currency = tenant.currency,
                    created = OffsetDateTime.now(),
                    updated = OffsetDateTime.now()
                )
            }

        return GetBalanceResponse(
            balance = balance.toBalance()
        )
    }
}