package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.core.logging.KVLogger
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.SearchTransactionRequest
import com.wutsi.platform.payment.dto.SearchTransactionResponse
import com.wutsi.platform.payment.service.TenantProvider
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
public class SearchTransactionDelegate(
    private val dao: TransactionRepository,
    private val tenantProvider: TenantProvider,
    private val logger: KVLogger,
) {
    public fun invoke(request: SearchTransactionRequest): SearchTransactionResponse {
        logger.add("account_id", request.accountId)
        logger.add("limit", request.limit)
        logger.add("offset", request.offset)

        val page = PageRequest.of(
            request.offset / request.limit,
            request.limit,
            Sort.by("created").descending()
        )
        val tenantId = tenantProvider.id()
        val txs = dao.findByTenantIdAndAccountId(tenantId, request.accountId, page)

        logger.add("count", txs.size)
        return SearchTransactionResponse(
            transactions = txs.map { it.toTransactionSummary() }
        )
    }
}
