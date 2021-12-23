package com.wutsi.platform.notification.service

import com.wutsi.platform.core.security.spring.ApplicationTokenProvider
import com.wutsi.platform.security.WutsiSecurityApi
import com.wutsi.platform.security.dto.AuthenticationRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class ApplicationTokenInitializer(
    private val securityApi: WutsiSecurityApi,
    private val applicationTokenProvider: ApplicationTokenProvider,

    @Value("\${wutsi.platform.security.api-key}") private val apiKey: String,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ApplicationTokenInitializer::class.java)
    }

    @Async
    fun init() {
        LOGGER.info("Authenticating the Application...")

        applicationTokenProvider.value = securityApi.authenticate(
            AuthenticationRequest(
                type = "application",
                apiKey = apiKey
            )
        ).accessToken
    }
}
