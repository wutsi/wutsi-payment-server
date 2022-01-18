package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.`delegate`.ApproveTransactionDelegate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.`annotation`.GetMapping
import org.springframework.web.bind.`annotation`.PathVariable
import org.springframework.web.bind.`annotation`.RestController
import kotlin.String

@RestController
public class ApproveTransactionController(
    private val `delegate`: ApproveTransactionDelegate
) {
    @GetMapping("/v1/transactions/{id}/approve")
    @PreAuthorize(value = "hasAuthority('payment-manage')")
    public fun invoke(@PathVariable(name = "id") id: String) {
        delegate.invoke(id)
    }
}
