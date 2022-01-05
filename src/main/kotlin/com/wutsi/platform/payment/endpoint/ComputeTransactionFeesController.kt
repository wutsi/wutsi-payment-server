package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.`delegate`.ComputeTransactionFeesDelegate
import com.wutsi.platform.payment.dto.ComputeTransactionFeesRequest
import com.wutsi.platform.payment.dto.ComputeTransactionFeesResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.`annotation`.PostMapping
import org.springframework.web.bind.`annotation`.RequestBody
import org.springframework.web.bind.`annotation`.RestController
import javax.validation.Valid

@RestController
public class ComputeTransactionFeesController(
    private val `delegate`: ComputeTransactionFeesDelegate
) {
    @PostMapping("/v1/transactions/fees")
    @PreAuthorize(value = "hasAuthority('payment-read')")
    public fun invoke(@Valid @RequestBody request: ComputeTransactionFeesRequest):
        ComputeTransactionFeesResponse = delegate.invoke(request)
}
