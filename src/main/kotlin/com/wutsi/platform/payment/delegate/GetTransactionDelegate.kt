package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.Parameter
import com.wutsi.platform.core.error.ParameterType
import com.wutsi.platform.core.error.exception.ForbiddenException
import com.wutsi.platform.core.error.exception.NotFoundException
import com.wutsi.platform.core.tracing.TracingContext
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.GetTransactionResponse
import com.wutsi.platform.payment.service.SecurityManager
import com.wutsi.platform.payment.util.ErrorURN
import org.springframework.stereotype.Service

@Service
public class GetTransactionDelegate(
    private val dao: TransactionRepository,
    private val securityManager: SecurityManager,
    private val tracingContext: TracingContext
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

        // Are you the owner
        if (tx.accountId != securityManager.currentUserId())
            throw ForbiddenException(
                error = Error(
                    code = ErrorURN.OWNERSHIP_ERROR.urn
                )
            )

        // Is this transaction belong to the tenant?
        if (tx.tenantId.toString() != tracingContext.tenantId())
            throw ForbiddenException(
                error = Error(
                    code = ErrorURN.INVALID_TENANT.urn
                )
            )

        return GetTransactionResponse(
            transaction = tx.toTransaction()
        )
    }
}
