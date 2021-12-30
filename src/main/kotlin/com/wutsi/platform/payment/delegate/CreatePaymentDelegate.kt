package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.payment.dto.CreatePaymentRequest
import com.wutsi.platform.payment.dto.CreatePaymentResponse
import org.springframework.stereotype.Service

@Service
public class CreatePaymentDelegate() {
    public fun invoke(request: CreatePaymentRequest): CreatePaymentResponse {
        TODO()
    }
}
