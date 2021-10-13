package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.`delegate`.CreatePayoutDelegate
import com.wutsi.platform.payment.dto.CreatePayoutRequest
import com.wutsi.platform.payment.dto.CreatePayoutResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.`annotation`.PostMapping
import org.springframework.web.bind.`annotation`.RequestBody
import org.springframework.web.bind.`annotation`.RestController
import javax.validation.Valid

@RestController
public class CreatePayoutController(
    private val `delegate`: CreatePayoutDelegate
) {
    @PostMapping("/v1/payouts")
    @PreAuthorize(value = "hasAuthority('payment-manage')")
    public fun invoke(@Valid @RequestBody request: CreatePayoutRequest): CreatePayoutResponse =
        delegate.invoke(request)
}
