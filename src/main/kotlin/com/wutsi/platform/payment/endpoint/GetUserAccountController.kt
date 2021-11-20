package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.delegate.GetUserAccountDelegate
import com.wutsi.platform.payment.dto.GetAccountResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.`annotation`.GetMapping
import org.springframework.web.bind.`annotation`.PathVariable
import org.springframework.web.bind.`annotation`.RestController
import javax.validation.constraints.NotNull
import kotlin.Long

@RestController
public class GetUserAccountController(
    private val `delegate`: GetUserAccountDelegate
) {
    @GetMapping("/v1/users/{user-id}/account")
    @PreAuthorize(value = "hasAuthority('payment-read')")
    public fun invoke(@PathVariable(name = "user-id") @NotNull userId: Long): GetAccountResponse =
        delegate.invoke(userId)
}
