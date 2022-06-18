package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.`delegate`.CreateCashinDelegate
import com.wutsi.platform.payment.dto.CreateCashinRequest
import com.wutsi.platform.payment.dto.CreateCashinResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.`annotation`.PostMapping
import org.springframework.web.bind.`annotation`.RequestBody
import org.springframework.web.bind.`annotation`.RestController
import javax.validation.Valid

@RestController
public class CreateCashinController(
    public val `delegate`: CreateCashinDelegate,
) {
    @PostMapping("/v1/transactions/cashins")
    @PreAuthorize(value = "hasAuthority('payment-manage')")
    public fun invoke(@Valid @RequestBody request: CreateCashinRequest): CreateCashinResponse =
        delegate.invoke(request)
}
