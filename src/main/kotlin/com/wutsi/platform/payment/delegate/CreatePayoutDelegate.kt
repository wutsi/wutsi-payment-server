package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.payment.dto.CreatePayoutRequest
import com.wutsi.platform.payment.dto.CreatePayoutResponse
import com.wutsi.platform.payment.service.PayoutService
import org.springframework.stereotype.Service

@Service
public class CreatePayoutDelegate(
    private val service: PayoutService
) {
    public fun invoke(request: CreatePayoutRequest): CreatePayoutResponse {
        val payout = service.payout(request)
        return CreatePayoutResponse(
            id = payout.id,
            status = payout.status.name
        )
    }
}
