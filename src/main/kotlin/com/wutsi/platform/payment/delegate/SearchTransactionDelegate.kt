package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.core.logging.KVLogger
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.SearchTransactionRequest
import com.wutsi.platform.payment.dto.SearchTransactionResponse
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.service.TenantProvider
import org.springframework.stereotype.Service
import javax.persistence.EntityManager
import javax.persistence.Query

@Service
public class SearchTransactionDelegate(
    private val dao: TransactionRepository,
    private val tenantProvider: TenantProvider,
    private val logger: KVLogger,
    private val em: EntityManager,
) {
    public fun invoke(request: SearchTransactionRequest): SearchTransactionResponse {
        logger.add("account_id", request.accountId)
        logger.add("status", request.status)
        logger.add("type", request.type)
        logger.add("payment_request_id", request.paymentRequestId)
        logger.add("limit", request.limit)
        logger.add("offset", request.offset)

        val query = em.createQuery(sql(request))
        parameters(request, query)
        val txs = query
            .setFirstResult(request.offset)
            .setMaxResults(request.limit)
            .resultList as List<TransactionEntity>

        logger.add("count", txs.size)
        return SearchTransactionResponse(
            transactions = txs.map { it.toTransactionSummary() }
        )
    }

    private fun sql(request: SearchTransactionRequest): String {
        val select = select()
        val where = where(request)
        return if (where.isNullOrEmpty())
            select
        else
            "$select WHERE $where ORDER BY a.created DESC"
    }

    private fun select(): String =
        "SELECT a FROM TransactionEntity a"

    private fun where(request: SearchTransactionRequest): String {
        val criteria = mutableListOf<String>()

        criteria.add("a.tenantId=:tenant_id")
        if (request.accountId != null)
            criteria.add("(a.accountId=:account_id OR a.recipientId=:account_id)")
        if (request.paymentRequestId != null)
            criteria.add("a.paymentRequestId = :payment_request_id")
        if (request.status != null)
            criteria.add("a.status = :status")
        if (request.type != null)
            criteria.add("a.type = :type")
        return criteria.joinToString(separator = " AND ")
    }

    private fun parameters(request: SearchTransactionRequest, query: Query) {
        query.setParameter("tenant_id", tenantProvider.id())
        if (request.accountId != null)
            query.setParameter("account_id", request.accountId)
        if (request.paymentRequestId != null)
            query.setParameter("payment_request_id", request.paymentRequestId)
        if (request.status != null)
            query.setParameter("status", Status.valueOf(request.status.uppercase()))
        if (request.type != null)
            query.setParameter("type", TransactionType.valueOf(request.type.uppercase()))
    }
}
