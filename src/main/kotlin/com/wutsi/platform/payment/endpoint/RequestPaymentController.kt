package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.`delegate`.RequestPaymentDelegate
import com.wutsi.platform.payment.dto.RequestPaymentRequest
import com.wutsi.platform.payment.dto.RequestPaymentResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.`annotation`.PostMapping
import org.springframework.web.bind.`annotation`.RequestBody
import org.springframework.web.bind.`annotation`.RestController
import javax.validation.Valid

@RestController
public class RequestPaymentController(
    private val `delegate`: RequestPaymentDelegate
) {
    @PostMapping("/v1/payments/request")
    @PreAuthorize(value = "hasAuthority('payment-manage')")
    public fun invoke(@Valid @RequestBody request: RequestPaymentRequest): RequestPaymentResponse =
        delegate.invoke(request)
}
