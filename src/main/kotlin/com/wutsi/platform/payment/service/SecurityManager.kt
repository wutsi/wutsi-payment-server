package com.wutsi.platform.payment.service

import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.exception.ForbiddenException
import com.wutsi.platform.core.security.WutsiPrincipal
import com.wutsi.platform.core.tracing.TracingContext
import com.wutsi.platform.payment.entity.BalanceEntity
import com.wutsi.platform.payment.entity.PaymentRequestEntity
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.error.ErrorURN
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class SecurityManager(
    private val tracingContext: TracingContext
) {
    fun currentUserId() = currentPrincipal().id.toLong()

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
        if (tx.tenantId.toString() != tracingContext.tenantId())
            throw ForbiddenException(
                error = Error(
                    code = ErrorURN.ILLEGAL_TENANT_ACCESS.urn
                )
            )
        return true
    }

    fun checkTenant(paymentRequest: PaymentRequestEntity): Boolean {
        if (paymentRequest.tenantId.toString() != tracingContext.tenantId())
            throw ForbiddenException(
                error = Error(
                    code = ErrorURN.ILLEGAL_TENANT_ACCESS.urn
                )
            )
        return true
    }

    fun checkOwnership(tx: TransactionEntity): Boolean {
        val userId = currentUserId()
        if (tx.accountId != userId && tx.recipientId != userId)
            throw ForbiddenException(
                error = Error(
                    code = ErrorURN.ILLEGAL_TRANSACTION_ACCESS.urn
                )
            )
        return true
    }
}
