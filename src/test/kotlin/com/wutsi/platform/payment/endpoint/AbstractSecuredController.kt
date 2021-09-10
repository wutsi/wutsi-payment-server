package com.wutsi.platform.payment.endpoint

import com.auth0.jwt.interfaces.RSAKeyProvider
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.core.security.spring.SpringAuthorizationRequestInterceptor
import com.wutsi.platform.core.security.spring.jwt.JWTBuilder
import com.wutsi.platform.core.security.spring.jwt.JWTSubjectType
import com.wutsi.platform.core.test.TestRSAKeyProvider
import com.wutsi.platform.core.test.TestTokenProvider
import com.wutsi.platform.core.test.TestTracingContext
import com.wutsi.platform.core.tracing.TracingContext
import com.wutsi.platform.core.tracing.spring.SpringTracingRequestInterceptor
import com.wutsi.platform.core.util.URN
import com.wutsi.platform.security.WutsiSecurityApi
import com.wutsi.platform.security.dto.GetKeyResponse
import com.wutsi.platform.security.dto.Key
import org.junit.jupiter.api.BeforeEach
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.web.client.RestTemplate
import java.util.Base64

abstract class AbstractSecuredController {
    private lateinit var keyProvider: RSAKeyProvider
    private lateinit var tracingContext: TracingContext

    @MockBean
    lateinit var securityApi: WutsiSecurityApi

    @BeforeEach
    open fun setUp() {
        tracingContext = TestTracingContext()

        keyProvider = TestRSAKeyProvider()

        val key = Key(
            algorithm = "RSA",
            content = Base64.getEncoder().encodeToString(keyProvider.getPublicKeyById("1").encoded)
        )
        doReturn(GetKeyResponse(key)).whenever(securityApi).getKey(any())
    }

    protected fun createResTemplate(
        scope: List<String> = emptyList(),
        subjectId: Long = -1,
        subjectType: JWTSubjectType = JWTSubjectType.JWT_SUBJECT_TYPE_USER,
        admin: Boolean = false
    ): RestTemplate {
        val rest = RestTemplate()

        val tokenProvider = TestTokenProvider(
            JWTBuilder(
                subject = subjectId.toString(),
                subjectName = URN.of("user", subjectId.toString()).value,
                subjectType = subjectType,
                scope = scope,
                keyProvider = keyProvider,
                admin = admin
            ).build()
        )

        rest.interceptors.add(SpringTracingRequestInterceptor(tracingContext))
        rest.interceptors.add(SpringAuthorizationRequestInterceptor(tokenProvider))
        return rest
    }
}
