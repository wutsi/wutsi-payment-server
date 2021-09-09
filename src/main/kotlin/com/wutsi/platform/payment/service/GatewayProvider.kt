package com.wutsi.platform.payment.service

import com.wutsi.platform.payment.Gateway
import com.wutsi.platform.payment.PaymentMethodProvider.PAYMENT_METHOD_PROVIDER_MTN
import com.wutsi.platform.payment.provider.mtn.MTNGateway
import org.springframework.stereotype.Service

@Service
class GatewayProvider(
    private val mtn: MTNGateway
) {
    fun get(provider: String): Gateway =
        when (provider) {
            PAYMENT_METHOD_PROVIDER_MTN.shortName -> mtn
            else -> throw IllegalStateException("Not supported: $provider")
        }
}
