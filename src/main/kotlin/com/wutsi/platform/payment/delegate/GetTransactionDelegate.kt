package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.Parameter
import com.wutsi.platform.core.error.ParameterType
import com.wutsi.platform.core.error.exception.NotFoundException
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.GetTransactionResponse
import com.wutsi.platform.payment.error.ErrorURN
import com.wutsi.platform.payment.service.SecurityManager
import org.springframework.stereotype.Service

@Service
public class GetTransactionDelegate(
    private val dao: TransactionRepository,
    private val securityManager: SecurityManager
) {
    public fun invoke(id: String): GetTransactionResponse {
        val tx = dao.findById(id)
            .orElseThrow {
                NotFoundException(
                    error = Error(
                        code = ErrorURN.TRANSACTION_NOT_FOUND.urn,
                        parameter = Parameter(
                            name = "id",
                            value = id,
                            type = ParameterType.PARAMETER_TYPE_PATH
                        )
                    )
                )
            }

        securityManager.checkTenant(tx)

        return GetTransactionResponse(
            transaction = tx.toTransaction()
        )
    }
}
