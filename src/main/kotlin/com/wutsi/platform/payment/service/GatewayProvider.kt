package com.wutsi.platform.payment.service

import com.wutsi.platform.payment.Gateway
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodProvider.MTN
import com.wutsi.platform.payment.provider.mtn.MTNGateway
import org.springframework.stereotype.Service

@Service
class GatewayProvider(
    private val mtn: MTNGateway
) {
    fun get(provider: PaymentMethodProvider): Gateway =
        when (provider) {
            MTN -> mtn
            else -> throw IllegalStateException("Not supported: $provider")
        }
}
