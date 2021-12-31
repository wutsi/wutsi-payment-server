package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.`delegate`.GetPaymentRequestDelegate
import com.wutsi.platform.payment.dto.GetPaymentRequestResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.`annotation`.GetMapping
import org.springframework.web.bind.`annotation`.PathVariable
import org.springframework.web.bind.`annotation`.RestController
import kotlin.String

@RestController
public class GetPaymentRequestController(
    private val `delegate`: GetPaymentRequestDelegate
) {
    @GetMapping("/v1/payment-requests/{id}")
    @PreAuthorize(value = "hasAuthority('payment-read')")
    public fun invoke(@PathVariable(name = "id") id: String): GetPaymentRequestResponse =
        delegate.invoke(id)
}
