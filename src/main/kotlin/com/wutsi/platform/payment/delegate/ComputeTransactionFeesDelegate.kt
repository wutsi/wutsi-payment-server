package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.SearchAccountRequest
import com.wutsi.platform.core.logging.KVLogger
import com.wutsi.platform.payment.dto.ComputeTransactionFeesRequest
import com.wutsi.platform.payment.dto.ComputeTransactionFeesResponse
import com.wutsi.platform.payment.service.FeesCalculator
import com.wutsi.platform.payment.service.SecurityManager
import com.wutsi.platform.payment.service.TenantProvider
import org.springframework.stereotype.Service

@Service
public class ComputeTransactionFeesDelegate(
    private val feesCalculator: FeesCalculator,
    private val logger: KVLogger,
    private val tenantProvider: TenantProvider,
    private val accountApi: WutsiAccountApi,
    private val securityManager: SecurityManager
) {
    public fun invoke(request: ComputeTransactionFeesRequest): ComputeTransactionFeesResponse {
        logger.add("amount", request.amount)
        logger.add("transaction_type", request.transactionType)
        logger.add("recipientId", request.recipientId)

        val tenant = tenantProvider.get()
        val accounts = accountApi.searchAccount(
            request = SearchAccountRequest(
                ids = listOfNotNull(securityManager.currentUserId(), request.recipientId)
            )
        ).accounts.map { it.id to it }.toMap()
        val response = feesCalculator.computeFees(request, tenant, accounts)

        logger.add("fees", response.fees)
        logger.add("apply_to_sender", response.applyToSender)
        return response
    }
}
