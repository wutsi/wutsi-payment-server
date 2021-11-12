package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.Parameter
import com.wutsi.platform.core.error.ParameterType.PARAMETER_TYPE_PATH
import com.wutsi.platform.core.error.exception.NotFoundException
import com.wutsi.platform.core.tracing.TracingContext
import com.wutsi.platform.payment.dao.UserRepository
import com.wutsi.platform.payment.dto.Account
import com.wutsi.platform.payment.dto.GetAccountResponse
import com.wutsi.platform.payment.util.ErrorURN
import org.springframework.stereotype.Service

@Service
class GetUserAccountDelegate(
    private val userDao: UserRepository,
    private val tracingContext: TracingContext
) {
    fun invoke(userId: Long): GetAccountResponse {
        val user = userDao.findById(userId)
            .orElseGet {
                throw NotFoundException(
                    error = Error(
                        code = ErrorURN.ACCOUNT_NOT_FOUND.urn,
                        parameter = Parameter(
                            name = "user-id",
                            value = userId,
                            type = PARAMETER_TYPE_PATH
                        )
                    )
                )
            }

        val account = user.accounts.find { it.tenantId == tracingContext.tenantId()?.toLong() }
            ?: throw NotFoundException(
                error = Error(
                    code = ErrorURN.ACCOUNT_NOT_FOUND.urn,
                    parameter = Parameter(
                        name = "user-id",
                        value = userId,
                        type = PARAMETER_TYPE_PATH
                    )
                )
            )

        return GetAccountResponse(
            account = Account(
                id = account.id!!,
                type = account.type.name,
                balance = account.balance,
                currency = account.currency,
                created = account.created,
                name = account.name
            )
        )
    }
}
