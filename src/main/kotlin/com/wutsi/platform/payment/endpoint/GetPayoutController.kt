package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.`delegate`.GetPayoutDelegate
import com.wutsi.platform.payment.dto.GetPayoutResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.`annotation`.GetMapping
import org.springframework.web.bind.`annotation`.PathVariable
import org.springframework.web.bind.`annotation`.RestController
import javax.validation.constraints.NotBlank
import kotlin.String

@RestController
public class GetPayoutController(
    private val `delegate`: GetPayoutDelegate
) {
    @GetMapping("/v1/payouts/{id}")
    @PreAuthorize(value = "hasAuthority('payment-read')")
    public fun invoke(@PathVariable(name = "id") @NotBlank id: String): GetPayoutResponse =
        delegate.invoke(id)
}
