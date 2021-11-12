package com.wutsi.platform.payment.service

import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.exception.InternalErrorException
import com.wutsi.platform.payment.Gateway
import com.wutsi.platform.payment.provider.mtn.MTNGateway
import com.wutsi.platform.payment.util.ErrorURN.GATEWAY_NOT_SUPPORTED
import org.springframework.stereotype.Service

@Service
class GatewayProvider(
    private val mtn: MTNGateway
) {
    fun get(provider: String): Gateway =
        when (provider.toLowerCase()) {
            "mtn" -> mtn
            else -> throw InternalErrorException(
                error = Error(
                    code = GATEWAY_NOT_SUPPORTED.urn,
                    data = mapOf("paymentMethodProvider" to provider)
                )
            )
        }
}
