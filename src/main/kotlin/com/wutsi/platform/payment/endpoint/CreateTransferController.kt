package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.`delegate`.CreateTransferDelegate
import com.wutsi.platform.payment.dto.CreateTransferRequest
import com.wutsi.platform.payment.dto.CreateTransferResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.`annotation`.PostMapping
import org.springframework.web.bind.`annotation`.RequestBody
import org.springframework.web.bind.`annotation`.RestController
import javax.validation.Valid

@RestController
public class CreateTransferController(
    private val `delegate`: CreateTransferDelegate
) {
    @PostMapping("/v1/transactions/transfers")
    @PreAuthorize(value = "hasAuthority('payment-manage')")
    public fun invoke(@Valid @RequestBody request: CreateTransferRequest): CreateTransferResponse =
        delegate.invoke(request)
}
