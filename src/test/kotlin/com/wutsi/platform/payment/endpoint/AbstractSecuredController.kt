package com.wutsi.platform.payment.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.Account
import com.wutsi.platform.account.dto.GetAccountResponse
import com.wutsi.platform.account.dto.GetPaymentMethodResponse
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.account.dto.Phone
import com.wutsi.platform.core.security.SubjectType
import com.wutsi.platform.core.security.SubjectType.USER
import com.wutsi.platform.core.security.spring.SpringAuthorizationRequestInterceptor
import com.wutsi.platform.core.security.spring.jwt.JWTBuilder
import com.wutsi.platform.core.test.TestRSAKeyProvider
import com.wutsi.platform.core.test.TestTokenProvider
import com.wutsi.platform.core.test.TestTracingContext
import com.wutsi.platform.core.tracing.TracingContext
import com.wutsi.platform.core.tracing.spring.SpringTracingRequestInterceptor
import com.wutsi.platform.payment.GatewayProvider
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodType
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.provider.om.OMGateway
import com.wutsi.platform.tenant.WutsiTenantApi
import com.wutsi.platform.tenant.dto.Fee
import com.wutsi.platform.tenant.dto.GetTenantResponse
import com.wutsi.platform.tenant.dto.Logo
import com.wutsi.platform.tenant.dto.MobileCarrier
import com.wutsi.platform.tenant.dto.PhonePrefix
import com.wutsi.platform.tenant.dto.Tenant
import org.junit.jupiter.api.BeforeEach
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.web.client.RestTemplate

abstract class AbstractSecuredController {
    companion object {
        const val USER_ID = 1L
        const val TENANT_ID = 1L
    }

    private lateinit var tracingContext: TracingContext

    @MockBean
    protected lateinit var accountApi: WutsiAccountApi

    @MockBean
    protected lateinit var tenantApi: WutsiTenantApi

    @MockBean
    protected lateinit var gatewayProvider: GatewayProvider

    protected lateinit var gateway: OMGateway

    protected lateinit var user: Account
    protected lateinit var paymentMethod: PaymentMethod

    protected lateinit var rest: RestTemplate

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
                        prefixes = listOf("+237745")
                    ),
                ),
                logos = listOf(
                    Logo(type = "PICTORIAL", url = "http://www.goole.com/images/orange.png")
                )
            )
        ),
        fees = listOf(
            Fee(
                transactionType = TransactionType.CHARGE.name,
                amount = 0.0,
                percent = 0.1
            ),
            Fee(
                transactionType = TransactionType.CASHIN.name,
                paymentMethodType = PaymentMethodType.MOBILE.name,
                amount = 0.0,
                percent = 0.025,
                applyToSender = true
            ),
            Fee(
                transactionType = TransactionType.CASHOUT.name,
                paymentMethodType = PaymentMethodType.MOBILE.name,
                amount = 0.0,
                percent = 0.020,
                applyToSender = true
            ),
//            Fee(
//                transactionType = TransactionType.TRANSFER.name,
//                amount = 100.0,
//                percent = 0.0,
//                applyToSender = true
//            ),
        )
    )

    @BeforeEach
    open fun setUp() {
        tracingContext = TestTracingContext(tenantId = TENANT_ID.toString())

        doReturn(GetTenantResponse(tenant)).whenever(tenantApi).getTenant(any())

        user = Account(
            id = USER_ID,
            displayName = "Ray Sponsible",
            language = "en",
            status = "ACTIVE",
            email = "ray.sponsible@gmail.com"
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

        gateway = mock()

        doReturn(gateway).whenever(gatewayProvider).get(any())

        rest = createResTemplate()
    }

    protected fun createResTemplate(
        scope: List<String> = listOf(
            "payment-manage",
            "payment-read",
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
        return rest
    }
}
