package com.wutsi.platform.payment.service

import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.exception.ForbiddenException
import com.wutsi.platform.core.security.SubjectType
import com.wutsi.platform.core.security.WutsiPrincipal
import com.wutsi.platform.core.tracing.TracingContext
import com.wutsi.platform.payment.entity.BalanceEntity
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.error.ErrorURN
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class SecurityManager(
    private val tracingContext: TracingContext
) {
    fun currentUserId() = if (currentPrincipal().type == SubjectType.USER)
        currentPrincipal().id.toLong()
    else
        null

    private fun currentPrincipal(): WutsiPrincipal =
        SecurityContextHolder.getContext().authentication.principal as WutsiPrincipal

    fun checkTenant(balance: BalanceEntity): Boolean {
        if (balance.tenantId != tracingContext.tenantId()!!.toLong())
            throw ForbiddenException(
                error = Error(
                    code = ErrorURN.ILLEGAL_TENANT_ACCESS.urn
                )
            )
        return true
    }

    fun checkTenant(tx: TransactionEntity): Boolean {
        val tenantId = tracingContext.tenantId()
            ?: return true

        if (tx.tenantId.toString() != tenantId)
            throw ForbiddenException(
                error = Error(
                    code = ErrorURN.ILLEGAL_TENANT_ACCESS.urn
                )
            )
        return true
    }
}
