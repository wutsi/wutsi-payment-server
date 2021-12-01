package com.wutsi.platform.payment.`delegate`

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
    private val tenantProvider: TenantProvider
) {
    public fun invoke(request: SearchTransactionRequest): SearchTransactionResponse {
        val page = PageRequest.of(
            request.offset / request.limit,
            request.limit,
            Sort.by("created").descending()
        )
        val tenantId = tenantProvider.id()
        val txs = dao.findByTenantIdAndAccountId(tenantId, request.accountId, page)

        return SearchTransactionResponse(
            transactions = txs.map { it.toTransactionSummary() }
        )
    }
}
