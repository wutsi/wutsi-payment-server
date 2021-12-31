package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.`delegate`.CreatePaymentRequestDelegate
import com.wutsi.platform.payment.dto.CreatePaymentRequestRequest
import com.wutsi.platform.payment.dto.CreatePaymentRequestResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.`annotation`.PostMapping
import org.springframework.web.bind.`annotation`.RequestBody
import org.springframework.web.bind.`annotation`.RestController
import javax.validation.Valid

@RestController
public class CreatePaymentRequestController(
    private val `delegate`: CreatePaymentRequestDelegate
) {
    @PostMapping("/v1/payment-requests")
    @PreAuthorize(value = "hasAuthority('payment-manage')")
    public fun invoke(@Valid @RequestBody request: CreatePaymentRequestRequest):
        CreatePaymentRequestResponse = delegate.invoke(request)
}
