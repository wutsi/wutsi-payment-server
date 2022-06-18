package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.`delegate`.GetTransactionDelegate
import com.wutsi.platform.payment.dto.GetTransactionResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.`annotation`.GetMapping
import org.springframework.web.bind.`annotation`.PathVariable
import org.springframework.web.bind.`annotation`.RestController
import kotlin.String

@RestController
public class GetTransactionController(
    public val `delegate`: GetTransactionDelegate,
) {
    @GetMapping("/v1/transactions/{id}")
    @PreAuthorize(value = "hasAuthority('payment-read')")
    public fun invoke(@PathVariable(name = "id") id: String): GetTransactionResponse =
        delegate.invoke(id)
}
