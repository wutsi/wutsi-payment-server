package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.delegate.CreateCashoutDelegate
import com.wutsi.platform.payment.dto.CreateCashoutRequest
import com.wutsi.platform.payment.dto.CreateCashoutResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.`annotation`.PostMapping
import org.springframework.web.bind.`annotation`.RequestBody
import org.springframework.web.bind.`annotation`.RestController
import javax.validation.Valid

@RestController
public class CreateCashoutController(
    private val `delegate`: CreateCashoutDelegate
) {
    @PostMapping("/v1/transactions/cashouts")
    @PreAuthorize(value = "hasAuthority('payment-manage')")
    public fun invoke(@Valid @RequestBody request: CreateCashoutRequest): CreateCashoutResponse =
        delegate.invoke(request)
}
