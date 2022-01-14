package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.Parameter
import com.wutsi.platform.core.error.ParameterType
import com.wutsi.platform.core.error.exception.NotFoundException
import com.wutsi.platform.payment.dto.GetBalanceResponse
import com.wutsi.platform.payment.error.ErrorURN
import org.springframework.stereotype.Service

@Service
class GetBalanceDelegate : AbstractDelegate() {
    fun invoke(accountId: Long): GetBalanceResponse {
        val balance = balanceDao.findByAccountId(accountId)
            .orElseThrow {
                NotFoundException(
                    error = Error(
                        code = ErrorURN.BALANCE_NOT_FOUND.urn,
                        parameter = Parameter(
                            name = "accountId",
                            value = accountId,
                            type = ParameterType.PARAMETER_TYPE_PATH
                        )
                    )
                )
            }
        securityManager.checkTenant(balance)

        logger.add("account_id", accountId)
        logger.add("balance", balance.amount)
        return GetBalanceResponse(
            balance = balance.toBalance()
        )
    }
}
