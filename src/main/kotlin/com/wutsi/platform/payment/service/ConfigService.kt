package com.wutsi.platform.payment.service

import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.Parameter
import com.wutsi.platform.core.error.exception.ConflictException
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.dao.ConfigRepository
import com.wutsi.platform.payment.entity.ConfigEntity
import com.wutsi.platform.payment.util.ErrorURN
import org.springframework.stereotype.Service

@Service
class ConfigService(
    private val dao: ConfigRepository
) {
    fun getConfig(provider: PaymentMethodProvider, country: String?): ConfigEntity {
        if (country == null)
            throw ConflictException(
                error = Error(
                    code = ErrorURN.PAYMENT_METHOD_NOT_SUPPORTED.urn
                )
            )

        return dao.findByPaymentMethodProviderAndCountry(provider, country)
            .orElseThrow {
                ConflictException(
                    error = Error(
                        code = ErrorURN.PAYMENT_METHOD_NOT_SUPPORTED.urn,
                        parameter = Parameter(
                            value = provider.name
                        )
                    )
                )
            }
    }

    fun checkSupport(paymentMethod: PaymentMethod) {
        val provider = PaymentMethodProvider.valueOf(paymentMethod.provider)
        getConfig(provider, paymentMethod.phone!!.country)
    }
}
