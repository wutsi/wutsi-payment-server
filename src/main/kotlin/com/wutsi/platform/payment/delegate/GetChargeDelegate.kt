package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.Parameter
import com.wutsi.platform.core.error.ParameterType.PARAMETER_TYPE_PATH
import com.wutsi.platform.core.error.exception.NotFoundException
import com.wutsi.platform.payment.dao.ChargeRepository
import com.wutsi.platform.payment.dto.Charge
import com.wutsi.platform.payment.dto.GetChargeResponse
import com.wutsi.platform.payment.util.ErrorURN.CHARGE_NOT_FOUND
import org.springframework.stereotype.Service

@Service
public class GetChargeDelegate(private val dao: ChargeRepository) {
    public fun invoke(id: String): GetChargeResponse {
        val charge = dao.findById(id)
            .orElseThrow {
                NotFoundException(
                    error = Error(
                        code = CHARGE_NOT_FOUND.urn,
                        parameter = Parameter(
                            name = "id",
                            value = id,
                            type = PARAMETER_TYPE_PATH
                        )
                    )
                )
            }

        return GetChargeResponse(
            charge = Charge(
                id = id,
                merchantId = charge.merchantId,
                applicationId = charge.applicationId,
                customerId = charge.customerId,
                userId = charge.userId,
                currency = charge.currency,
                supplierErrorCode = charge.supplierErrorCode,
                errorCode = charge.errorCode?.name,
                gatewayTransactionId = charge.gatewayTransactionId,
                paymentMethodToken = charge.paymentMethodToken,
                description = charge.description,
                externalId = charge.externalId,
                amount = charge.amount,
                status = charge.status.name,
                paymentMethodType = charge.paymentMethodType.name,
                paymentMethodProvider = charge.paymentMethodProvider.name,
                created = charge.created,
                updated = charge.updated,
                financialTransactionId = charge.financialTransactionId
            )
        )
    }
}
