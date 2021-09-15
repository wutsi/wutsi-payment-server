package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.delegate.GetBalanceDelegate
import com.wutsi.platform.payment.dto.GetBalanceResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.validation.constraints.NotNull

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
public class GetBalanceController(
    private val `delegate`: GetBalanceDelegate
) {
    @GetMapping("/v1/balances")
    @PreAuthorize(value = "hasAuthority('payment-read')")
    public fun invoke(@RequestParam(name = "account-id", required = true) @NotNull accountId: Long):
        GetBalanceResponse = delegate.invoke(accountId)
}
