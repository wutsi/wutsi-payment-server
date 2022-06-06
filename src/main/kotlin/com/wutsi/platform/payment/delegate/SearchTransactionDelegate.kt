package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.core.logging.KVLogger
import com.wutsi.platform.core.tracing.TracingContext
import com.wutsi.platform.payment.core.Status
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
    private val tenantProvider: TenantProvider,
    private val logger: KVLogger,
    private val em: EntityManager,
    private val tracingContext: TracingContext
) {
    public fun invoke(request: SearchTransactionRequest): SearchTransactionResponse {
        logger.add("account_id", request.accountId)
        logger.add("status", request.status)
        logger.add("type", request.type)
        logger.add("order_id", request.orderId)
        logger.add("limit", request.limit)
        logger.add("offset", request.offset)

        val sql = sql(request)
        logger.add("sql", sql)

        val query = em.createQuery(sql)
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

        if (tracingContext.tenantId() != null)
            criteria.add("a.tenantId=:tenant_id")
        if (request.accountId != null)
            criteria.add("(a.accountId=:account_id OR a.recipientId=:account_id)")
        if (request.status.isNotEmpty())
            criteria.add("a.status IN :status")
        if (request.type != null)
            criteria.add("a.type=:type")
        if (request.orderId != null)
            criteria.add("a.orderId=:order_id")
        return criteria.joinToString(separator = " AND ")
    }

    private fun parameters(request: SearchTransactionRequest, query: Query) {
        if (tracingContext.tenantId() != null)
            query.setParameter("tenant_id", tracingContext.tenantId()!!.toLong())
        if (request.accountId != null)
            query.setParameter("account_id", request.accountId)
        if (request.status.isNotEmpty())
            query.setParameter("status", request.status.map { Status.valueOf(it.uppercase()) })
        if (request.type != null)
            query.setParameter("type", TransactionType.valueOf(request.type.uppercase()))
        if (request.orderId != null)
            query.setParameter("order_id", request.orderId)
    }
}
