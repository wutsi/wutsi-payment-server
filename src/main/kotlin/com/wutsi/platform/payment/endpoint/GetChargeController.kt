package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.`delegate`.GetChargeDelegate
import com.wutsi.platform.payment.dto.GetChargeResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.`annotation`.GetMapping
import org.springframework.web.bind.`annotation`.PathVariable
import org.springframework.web.bind.`annotation`.RestController
import javax.validation.constraints.NotBlank
import kotlin.String

@RestController
public class GetChargeController(
    private val `delegate`: GetChargeDelegate
) {
    @GetMapping("/v1/charges/{id}")
    @PreAuthorize(value = "hasAuthority('payment-read')")
    public fun invoke(@PathVariable(name = "id") @NotBlank id: String): GetChargeResponse =
        delegate.invoke(id)
}
