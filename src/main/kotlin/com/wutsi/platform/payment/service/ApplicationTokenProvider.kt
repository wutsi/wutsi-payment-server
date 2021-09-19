package com.wutsi.platform.payment.service

import com.wutsi.platform.core.security.TokenProvider
import com.wutsi.platform.security.WutsiSecurityApi
import com.wutsi.platform.security.dto.AuthenticationRequest
import org.springframework.stereotype.Service

@Service
class ApplicationTokenProvider(
    private val securityApi: WutsiSecurityApi
) : TokenProvider {
    companion object {
        private const val APP_NAME = "com.wutsi.wutsi-payment"
    }

    private var accessToken: String? = null

    override fun getToken(): String? {
        if (accessToken == null)
            authenticate()

        return accessToken
    }

    private fun authenticate() {
        val apps = securityApi.searchApplications(name = APP_NAME).applications
        this.accessToken = securityApi.authenticate(
            AuthenticationRequest(
                type = "APPLICATION",
                apiKey = apps[0].apiKey
            )
        ).accessToken
    }
}
