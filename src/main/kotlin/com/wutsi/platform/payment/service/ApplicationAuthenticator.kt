package com.wutsi.platform.payment.service

import com.wutsi.platform.core.security.spring.ApiKeyAuthenticator
import com.wutsi.platform.security.WutsiSecurityApi
import com.wutsi.platform.security.dto.AuthenticationRequest
import org.springframework.stereotype.Service

@Service
class ApplicationAuthenticator(
    private val securityApi: WutsiSecurityApi
) : ApiKeyAuthenticator {
    override fun authenticate(apiKey: String): String =
        securityApi.authenticate(
            AuthenticationRequest(
                type = "application",
                apiKey = apiKey
            )
        ).accessToken
}
