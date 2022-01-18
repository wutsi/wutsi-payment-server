package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.Parameter
import com.wutsi.platform.core.error.ParameterType
import com.wutsi.platform.core.error.exception.ConflictException
import com.wutsi.platform.core.error.exception.ForbiddenException
import com.wutsi.platform.core.error.exception.NotFoundException
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.core.ErrorCode
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.error.ErrorURN
import com.wutsi.platform.payment.error.TransactionException
import com.wutsi.platform.payment.service.TenantProvider
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
public class ApproveTransactionDelegate(
    private val dao: TransactionRepository,
    private val delegate: CreateTransferDelegate,
    private val tenantProvider: TenantProvider
) : AbstractDelegate() {
    @Transactional(noRollbackFor = [TransactionException::class])
    public fun invoke(id: String) {
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

        val tenant = tenantProvider.get()

        // Validations
        checkPermission(tx, tenant)

        try {
            checkApprovalRules(tx)

            // Check expiry
            val now = OffsetDateTime.now()
            if (tx.expires != null && now.isAfter(tx.expires)) {
                throw PaymentException(
                    error = com.wutsi.platform.payment.core.Error(
                        code = ErrorCode.EXPIRED,
                        transactionId = id
                    )
                )
            }

            // Approve
            tx.status = Status.SUCCESSFUL
            tx.approved = now
            tx.requiresApproval = false
            dao.save(tx)
            delegate.onSuccess(tx, tenant)
        } catch (ex: PaymentException) {
            delegate.onFailure(tx, ex)

            throw TransactionException(
                error = Error(
                    code = ErrorURN.TRANSACTION_FAILED.urn,
                    downstreamCode = tx.errorCode,
                    data = mapOf("id" to tx.id!!)
                )
            )
        }
    }

    private fun checkApprovalRules(tx: TransactionEntity) {
        if (tx.status != Status.PENDING)
            throw ConflictException(
                error = Error(
                    code = ErrorURN.TRANSACTION_NOT_PENDING.urn,
                )
            )

        if (!tx.requiresApproval)
            throw ConflictException(
                error = Error(
                    code = ErrorURN.NO_APPROVAL_REQUIRED.urn,
                )
            )

        if (tx.type != TransactionType.TRANSFER)
            throw ConflictException(
                error = Error(
                    code = ErrorURN.TRANSACTION_NOT_TRANSFER.urn,
                    message = "You are not authorize to approve ${tx.type}"
                )
            )
    }

    private fun checkPermission(tx: TransactionEntity, tenant: Tenant) {
        if (tx.recipientId != securityManager.currentUserId())
            throw ForbiddenException(
                error = Error(
                    code = ErrorURN.ILLEGAL_APPROVER.urn,
                )
            )

        if (tenant.id != tx.tenantId)
            throw ForbiddenException(
                error = Error(
                    code = ErrorURN.ILLEGAL_TENANT_ACCESS.urn
                )
            )
    }
}
