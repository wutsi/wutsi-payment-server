package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.SearchTransactionRequest
import com.wutsi.platform.payment.dto.SearchTransactionResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
public class SearchTransactionDelegate(
    private val dao: TransactionRepository
) {
    public fun invoke(request: SearchTransactionRequest): SearchTransactionResponse {
        val page = PageRequest.of(
            request.offset / request.limit,
            request.limit,
            Sort.by("created").descending()
        )
        val txs = dao.findByTenantIdAndUserId(request.tenantId, request.userId, page)

        return SearchTransactionResponse(
            transactions = txs.map { it.toTransactionSummary() }
        )
    }
}
