package com.wutsi.platform.payment.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.Account
import com.wutsi.platform.account.dto.GetAccountResponse
import com.wutsi.platform.account.dto.GetPaymentMethodResponse
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.account.dto.Phone
import com.wutsi.platform.core.security.ApiKeyProvider
import com.wutsi.platform.core.security.SubjectType
import com.wutsi.platform.core.security.SubjectType.USER
import com.wutsi.platform.core.security.spring.SpringApiKeyRequestInterceptor
import com.wutsi.platform.core.security.spring.SpringAuthorizationRequestInterceptor
import com.wutsi.platform.core.security.spring.jwt.JWTBuilder
import com.wutsi.platform.core.test.TestApiKeyProvider
import com.wutsi.platform.core.test.TestRSAKeyProvider
import com.wutsi.platform.core.test.TestTokenProvider
import com.wutsi.platform.core.test.TestTracingContext
import com.wutsi.platform.core.tracing.TracingContext
import com.wutsi.platform.core.tracing.spring.SpringTracingRequestInterceptor
import com.wutsi.platform.payment.GatewayProvider
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodType
import com.wutsi.platform.payment.provider.mtn.MTNGateway
import com.wutsi.platform.payment.provider.om.OMGateway
import com.wutsi.platform.tenant.WutsiTenantApi
import com.wutsi.platform.tenant.dto.GetTenantResponse
import com.wutsi.platform.tenant.dto.Logo
import com.wutsi.platform.tenant.dto.MobileCarrier
import com.wutsi.platform.tenant.dto.PhonePrefix
import com.wutsi.platform.tenant.dto.Tenant
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.web.client.RestTemplate

abstract class AbstractSecuredController {
    companion object {
        const val USER_ID = 1L
        const val TENANT_ID = 1L
    }

    private lateinit var apiKeyProvider: ApiKeyProvider
    private lateinit var tracingContext: TracingContext

    @MockBean
    protected lateinit var accountApi: WutsiAccountApi

    @MockBean
    protected lateinit var tenantApi: WutsiTenantApi

    @Autowired
    protected lateinit var gatewayProvider: GatewayProvider

    @MockBean
    protected lateinit var omGateway: OMGateway

    @MockBean
    protected lateinit var mtnGateway: MTNGateway

    protected lateinit var user: Account
    protected lateinit var paymentMethod: PaymentMethod

    protected lateinit var rest: RestTemplate

    @BeforeEach
    open fun setUp() {
        tracingContext = TestTracingContext(tenantId = TENANT_ID.toString())
        apiKeyProvider = TestApiKeyProvider("00000000-00000000-00000000-00000000")

        val tenant = Tenant(
            id = 1,
            name = "test",
            logos = listOf(
                Logo(type = "PICTORIAL", url = "http://www.goole.com/images/1.png")
            ),
            countries = listOf("CM"),
            languages = listOf("en", "fr"),
            currency = "XAF",
            domainName = "www.wutsi.com",
            mobileCarriers = listOf(
                MobileCarrier(
                    code = "mtn",
                    name = "MTN",
                    countries = listOf("CM", "CD"),
                    phonePrefixes = listOf(
                        PhonePrefix(
                            country = "CM",
                            prefixes = listOf("+23795")
                        ),
                    ),
                    logos = listOf(
                        Logo(type = "PICTORIAL", url = "http://www.goole.com/images/mtn.png")
                    )
                ),
                MobileCarrier(
                    code = "orange",
                    name = "ORANGE",
                    countries = listOf("CM"),
                    phonePrefixes = listOf(
                        PhonePrefix(
                            country = "CM",
                            prefixes = listOf("+23722")
                        ),
                    ),
                    logos = listOf(
                        Logo(type = "PICTORIAL", url = "http://www.goole.com/images/orange.png")
                    )
                )
            )
        )
        doReturn(GetTenantResponse(tenant)).whenever(tenantApi).getTenant(any())

        user = Account(
            id = USER_ID,
            displayName = "Ray Sponsible",
            language = "en",
            status = "ACTIVE",
        )
        doReturn(GetAccountResponse(user)).whenever(accountApi).getAccount(any())

        paymentMethod = PaymentMethod(
            token = "xxxx",
            type = PaymentMethodType.MOBILE.name,
            provider = PaymentMethodProvider.MTN.name,
            phone = Phone(
                number = "+237995076666"
            ),
            ownerName = user.displayName!!
        )
        doReturn(GetPaymentMethodResponse(paymentMethod)).whenever(accountApi).getPaymentMethod(any(), any())

        doReturn(PaymentMethodProvider.ORANGE).whenever(omGateway).provider()
        doReturn(PaymentMethodProvider.MTN).whenever(mtnGateway).provider()
        gatewayProvider.register(mtnGateway)
        gatewayProvider.register(omGateway)

        rest = createResTemplate()
    }

    protected fun createResTemplate(
        scope: List<String> = listOf(
            "payment-manage",
            "payment-read"
        ),
        subjectId: Long = USER_ID,
        subjectType: SubjectType = USER,
        admin: Boolean = false
    ): RestTemplate {
        val rest = RestTemplate()

        val tokenProvider = TestTokenProvider(
            JWTBuilder(
                subject = subjectId.toString(),
                subjectType = subjectType,
                scope = scope,
                keyProvider = TestRSAKeyProvider(),
                admin = admin
            ).build()
        )

        rest.interceptors.add(SpringTracingRequestInterceptor(tracingContext))
        rest.interceptors.add(SpringAuthorizationRequestInterceptor(tokenProvider))
        rest.interceptors.add(SpringApiKeyRequestInterceptor(apiKeyProvider))
        return rest
    }
}
