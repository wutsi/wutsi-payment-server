package com.wutsi.platform.payment.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.wutsi.platform.account.Environment.PRODUCTION
import com.wutsi.platform.account.Environment.SANDBOX
import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.WutsiAccountApiBuilder
import com.wutsi.platform.core.security.feign.FeignAuthorizationRequestInterceptor
import com.wutsi.platform.core.tracing.feign.FeignTracingRequestInterceptor
import com.wutsi.platform.payment.service.ApplicationTokenProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles

@Configuration
public class AccountApiConfiguration(
    private val applicationTokenProvider: ApplicationTokenProvider,
    private val tracingRequestInterceptor: FeignTracingRequestInterceptor,
    private val mapper: ObjectMapper,
    private val env: Environment
) {
    @Bean
    fun accountApi(): WutsiAccountApi =
        WutsiAccountApiBuilder().build(
            env = environment(),
            mapper = mapper,
            interceptors = listOf(
                tracingRequestInterceptor,
                FeignAuthorizationRequestInterceptor(applicationTokenProvider)
            )
        )

    private fun environment(): com.wutsi.platform.account.Environment =
        if (env.acceptsProfiles(Profiles.of("prod")))
            PRODUCTION
        else
            SANDBOX
}
