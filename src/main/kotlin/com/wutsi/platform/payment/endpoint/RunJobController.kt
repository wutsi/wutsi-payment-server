package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.`delegate`.RunJobDelegate
import com.wutsi.platform.payment.dto.RunJobResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.`annotation`.PathVariable
import org.springframework.web.bind.`annotation`.PostMapping
import org.springframework.web.bind.`annotation`.RestController
import javax.validation.constraints.NotBlank
import kotlin.String

@RestController
public class RunJobController(
    private val `delegate`: RunJobDelegate
) {
    @PostMapping("/v1/jobs/{name}")
    @PreAuthorize(value = "hasAuthority('payment-job-manage')")
    public fun invoke(@PathVariable(name = "name") @NotBlank name: String): RunJobResponse =
        delegate.invoke(name)
}
