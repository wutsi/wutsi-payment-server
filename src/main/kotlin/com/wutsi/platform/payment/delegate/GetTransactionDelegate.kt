package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.Parameter
import com.wutsi.platform.core.error.ParameterType
import com.wutsi.platform.core.error.exception.NotFoundException
import com.wutsi.platform.core.logging.KVLogger
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.GetTransactionResponse
import com.wutsi.platform.payment.error.ErrorURN
import com.wutsi.platform.payment.service.SecurityManager
import org.springframework.stereotype.Service

@Service
public class GetTransactionDelegate(
    private val dao: TransactionRepository,
    private val securityManager: SecurityManager,
    private val logger: KVLogger
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

        logger.add("transaction_id", tx.id)
        logger.add("transaction_type", tx.type)
        logger.add("transaction_status", tx.status)
        logger.add("transaction_error_code", tx.errorCode)
        logger.add("transaction_supplier_error_code", tx.supplierErrorCode)
        logger.add("transaction_amount", tx.amount)
        logger.add("transaction_fees", tx.fees)
        logger.add("transaction_net", tx.net)
        logger.add("transaction_gateway_fees", tx.gatewayFees)
        logger.add("transaction_account_id", tx.accountId)
        logger.add("transaction_recipient_id", tx.recipientId)
        logger.add("transaction_order_id", tx.orderId)
        logger.add("transaction_business", tx.business)
        logger.add("transaction_currency", tx.currency)
        logger.add("transaction_payment_method_provider", tx.paymentMethodProvider)
        return GetTransactionResponse(
            transaction = tx.toTransaction()
        )
    }
}
