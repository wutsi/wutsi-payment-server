package com.wutsi.platform.payment.service

import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.Account
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.Parameter
import com.wutsi.platform.core.error.ParameterType
import com.wutsi.platform.core.error.exception.NotFoundException
import com.wutsi.platform.payment.util.ErrorURN.ACCOUNT_NOT_ACTIVE
import org.springframework.stereotype.Service

@Service
class AccountService(
    private val api: WutsiAccountApi
) {
    fun findAccount(id: Long, parameterName: String? = null, parameterType: ParameterType? = null): Account {
        val account = api.getAccount(id).account
        if (!"ACTIVE".equals(account.status, true))
            throw NotFoundException(
                error = Error(
                    code = ACCOUNT_NOT_ACTIVE.urn,
                    message = "Invalid status: ${account.status}",
                    parameter = parameterName?.let {
                        Parameter(
                            name = it,
                            value = id,
                            type = parameterType
                        )
                    }
                )
            )
        return account
    }

    fun findPaymentMethod(accountId: Long, token: String): PaymentMethod =
        api.getPaymentMethod(accountId, token).paymentMethod
}
