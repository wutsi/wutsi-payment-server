package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.`delegate`.GetBalanceDelegate
import com.wutsi.platform.payment.dto.GetBalanceResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.`annotation`.GetMapping
import org.springframework.web.bind.`annotation`.RequestParam
import org.springframework.web.bind.`annotation`.RestController
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import kotlin.Long
import kotlin.String

@RestController
public class GetBalanceController(
    private val `delegate`: GetBalanceDelegate
) {
    @GetMapping("/v1/balances")
    @PreAuthorize(value = "hasAuthority('payment-read')")
    public fun invoke(
        @RequestParam(name = "account-id", required = true) @NotNull accountId: Long,
        @RequestParam(name = "payment-method-provider", required = true) @NotBlank
        paymentMethodProvider: String
    ): GetBalanceResponse = delegate.invoke(
        accountId,
        paymentMethodProvider
    )
}
