package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.`delegate`.GetBalanceDelegate
import com.wutsi.platform.payment.dto.GetBalanceResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.`annotation`.GetMapping
import org.springframework.web.bind.`annotation`.PathVariable
import org.springframework.web.bind.`annotation`.RestController
import javax.validation.constraints.NotNull
import kotlin.Long

@RestController
public class GetBalanceController(
    private val `delegate`: GetBalanceDelegate
) {
    @GetMapping("/v1/accounts/{account-id}/balance")
    @PreAuthorize(value = "hasAuthority('payment-read')")
    public fun invoke(@PathVariable(name = "account-id") @NotNull accountId: Long): GetBalanceResponse =
        delegate.invoke(accountId)
}
