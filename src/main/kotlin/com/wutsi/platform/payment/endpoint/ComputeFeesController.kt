package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.`delegate`.ComputeFeesDelegate
import com.wutsi.platform.payment.dto.ComputeFeesRequest
import com.wutsi.platform.payment.dto.ComputeFeesResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.`annotation`.PostMapping
import org.springframework.web.bind.`annotation`.RequestBody
import org.springframework.web.bind.`annotation`.RestController
import javax.validation.Valid

@RestController
public class ComputeFeesController(
    private val `delegate`: ComputeFeesDelegate
) {
    @PostMapping("/v1/transactions/fees")
    @PreAuthorize(value = "hasAuthority('payment-manage')")
    public fun invoke(@Valid @RequestBody request: ComputeFeesRequest): ComputeFeesResponse =
        delegate.invoke(request)
}
