package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.`delegate`.SearchTransactionDelegate
import com.wutsi.platform.payment.dto.SearchTransactionRequest
import com.wutsi.platform.payment.dto.SearchTransactionResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.`annotation`.PostMapping
import org.springframework.web.bind.`annotation`.RequestBody
import org.springframework.web.bind.`annotation`.RestController
import javax.validation.Valid

@RestController
public class SearchTransactionController(
    private val `delegate`: SearchTransactionDelegate
) {
    @PostMapping("/v1/transactions/search")
    @PreAuthorize(value = "hasAuthority('payment-read')")
    public fun invoke(@Valid @RequestBody request: SearchTransactionRequest):
        SearchTransactionResponse = delegate.invoke(request)
}
