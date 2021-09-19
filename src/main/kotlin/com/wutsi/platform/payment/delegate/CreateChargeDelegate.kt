package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.payment.dto.CreateChargeRequest
import com.wutsi.platform.payment.dto.CreateChargeResponse
import com.wutsi.platform.payment.service.ChargeService
import org.springframework.stereotype.Service

@Service
class CreateChargeDelegate(
    private val service: ChargeService
) {
    fun invoke(request: CreateChargeRequest): CreateChargeResponse {
        val charge = service.charge(request)
        return CreateChargeResponse(
            id = charge.id,
            status = charge.status.name
        )
    }
}
