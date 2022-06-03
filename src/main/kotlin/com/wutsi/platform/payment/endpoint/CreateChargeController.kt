package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.`delegate`.CreateChargeDelegate
import com.wutsi.platform.payment.dto.CreateChargeRequest
import com.wutsi.platform.payment.dto.CreateChargeResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.`annotation`.PostMapping
import org.springframework.web.bind.`annotation`.RequestBody
import org.springframework.web.bind.`annotation`.RestController
import javax.validation.Valid

@RestController
public class CreateChargeController(
    private val `delegate`: CreateChargeDelegate
) {
    @PostMapping("/v1/transactions/charges")
    @PreAuthorize(value = "hasAuthority('payment-manage')")
    public fun invoke(@Valid @RequestBody request: CreateChargeRequest): CreateChargeResponse =
        delegate.invoke(request)
}
