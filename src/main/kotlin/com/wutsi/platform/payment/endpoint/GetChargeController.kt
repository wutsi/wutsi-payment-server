package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.delegate.GetChargeDelegate
import com.wutsi.platform.payment.dto.GetChargeResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import javax.validation.constraints.NotBlank

@RestController
@CrossOrigin(
    origins = ["*"],
    allowedHeaders = ["Content-Type", "Authorization", "Content-Length", "X-Requested-With"],
    methods = [
        org.springframework.web.bind.annotation.RequestMethod.GET,
        org.springframework.web.bind.annotation.RequestMethod.DELETE,
        org.springframework.web.bind.annotation.RequestMethod.OPTIONS,
        org.springframework.web.bind.annotation.RequestMethod.HEAD,
        org.springframework.web.bind.annotation.RequestMethod.POST,
        org.springframework.web.bind.annotation.RequestMethod.PUT
    ]
)
public class GetChargeController(
    private val `delegate`: GetChargeDelegate
) {
    @GetMapping("/v1/charges/{id}")
    @PreAuthorize(value = "hasAuthority('payment-charge')")
    public fun invoke(@PathVariable(name = "id") @NotBlank id: String): GetChargeResponse =
        delegate.invoke(id)
}
