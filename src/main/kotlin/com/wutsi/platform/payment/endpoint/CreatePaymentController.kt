package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.`delegate`.CreatePaymentDelegate
import com.wutsi.platform.payment.dto.CreatePaymentRequest
import com.wutsi.platform.payment.dto.CreatePaymentResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.`annotation`.PostMapping
import org.springframework.web.bind.`annotation`.RequestBody
import org.springframework.web.bind.`annotation`.RestController
import javax.validation.Valid

@RestController
public class CreatePaymentController(
    private val `delegate`: CreatePaymentDelegate
) {
    @PostMapping("/v1/transactions/payments")
    @PreAuthorize(value = "hasAuthority('payment-manage')")
    public fun invoke(@Valid @RequestBody request: CreatePaymentRequest): CreatePaymentResponse =
        delegate.invoke(request)
}
