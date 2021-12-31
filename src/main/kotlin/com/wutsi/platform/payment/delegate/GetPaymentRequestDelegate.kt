package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.Parameter
import com.wutsi.platform.core.error.ParameterType
import com.wutsi.platform.core.error.exception.NotFoundException
import com.wutsi.platform.payment.dao.PaymentRequestRepository
import com.wutsi.platform.payment.dto.GetPaymentRequestResponse
import com.wutsi.platform.payment.error.ErrorURN
import com.wutsi.platform.payment.service.SecurityManager
import org.springframework.stereotype.Service

@Service
public class GetPaymentRequestDelegate(
    private val dao: PaymentRequestRepository,
    private val securityManager: SecurityManager
) {
    public fun invoke(id: String): GetPaymentRequestResponse {
        val paymentRequest = dao.findById(id)
            .orElseThrow {
                NotFoundException(
                    error = Error(
                        code = ErrorURN.PAYMENT_REQUEST_NOT_FOUND.urn,
                        parameter = Parameter(
                            name = "id",
                            value = id,
                            type = ParameterType.PARAMETER_TYPE_PATH
                        )
                    )
                )
            }

        securityManager.checkTenant(paymentRequest)

        return GetPaymentRequestResponse(
            paymentRequest = paymentRequest.toPaymentRequest()
        )
    }
}
