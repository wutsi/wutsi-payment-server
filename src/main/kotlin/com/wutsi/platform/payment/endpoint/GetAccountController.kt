package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.`delegate`.GetAccountDelegate
import com.wutsi.platform.payment.dto.GetAccountResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.`annotation`.PathVariable
import org.springframework.web.bind.`annotation`.PostMapping
import org.springframework.web.bind.`annotation`.RestController
import javax.validation.constraints.NotNull
import kotlin.Long

@RestController
public class GetAccountController(
    private val `delegate`: GetAccountDelegate
) {
    @PostMapping("/v1/accounts/{id}")
    @PreAuthorize(value = "hasAuthority('payment-read')")
    public fun invoke(@PathVariable(name = "id") @NotNull id: Long): GetAccountResponse =
        delegate.invoke(id)
}
