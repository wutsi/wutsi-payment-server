package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.payment.dto.ComputeFeesRequest
import com.wutsi.platform.payment.dto.ComputeFeesResponse
import com.wutsi.platform.payment.dto.TransactionFee
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.service.FeesCalculator
import com.wutsi.platform.payment.service.TenantProvider
import org.springframework.stereotype.Service

@Service
public class ComputeFeesDelegate(
    private val feesCalculator: FeesCalculator,
    private val tenantProvider: TenantProvider
) {
    public fun invoke(request: ComputeFeesRequest): ComputeFeesResponse {
        val tenant = tenantProvider.get()
        val tx = TransactionEntity(
            currency = request.currency,
            amount = request.amount,
            type = TransactionType.valueOf(request.transactionType.uppercase())
        )
        feesCalculator.apply(tx, request.paymentMethodType, tenant)

        return ComputeFeesResponse(
            fee = TransactionFee(
                applyFeesToSender = tx.applyFeesToSender,
                amount = request.amount,
                fees = tx.fees,
                currency = request.currency,
                senderAmount = tx.amount,
                recipientAmount = tx.net
            )
        )
    }
}
