package com.wutsi.platform.payment.endpoint

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.platform.core.error.ErrorResponse
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.core.ErrorCode
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.BalanceRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.error.ErrorURN
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.event.TransactionEventPayload
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.jdbc.Sql
import org.springframework.web.client.HttpClientErrorException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/ApproveTransactionController.sql"])
public class ApproveTransactionControllerTest : AbstractSecuredController() {
    @LocalServerPort
    public val port: Int = 0
    private lateinit var url: String

    @Autowired
    private lateinit var txDao: TransactionRepository

    @Autowired
    private lateinit var balanceDao: BalanceRepository

    @MockBean
    private lateinit var eventStream: EventStream

    @Test
    @Sql(value = ["/db/clean.sql", "/db/ApproveTransactionController.sql"])
    public fun success() {
        // WHEN
        url = "http://localhost:$port/v1/transactions/100/approve"
        val response = rest.getForEntity(url, Any::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        val tx = txDao.findById("100").get()
        assertEquals(1L, tx.tenantId)
        assertEquals(Status.SUCCESSFUL, tx.status)
        assertNull(tx.errorCode)
        assertFalse(tx.requiresApproval)
        assertNotNull(tx.approved)

        val balance = balanceDao.findByAccountId(USER_ID).get()
        assertEquals(200000 + tx.net, balance.amount)

        val balance2 = balanceDao.findByAccountId(100L).get()
        assertEquals(100000 - tx.amount, balance2.amount)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_SUCCESSFUL.urn), payload.capture())
        assertEquals(TransactionType.TRANSFER.name, payload.firstValue.type)
        assertEquals(tx.id, payload.firstValue.transactionId)
    }

    @Test
    @Sql(value = ["/db/clean.sql", "/db/ApproveTransactionController.sql"])
    public fun expired() {
        // WHEN
        url = "http://localhost:$port/v1/transactions/900/approve"
        val ex = assertThrows<HttpClientErrorException> {
            rest.getForEntity(url, Any::class.java)
        }

        // THEN
        assertEquals(409, ex.rawStatusCode)

        val error = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java).error
        assertEquals(ErrorURN.TRANSACTION_FAILED.urn, error.code)

        val tx = txDao.findById("900").get()
        assertEquals(1L, tx.tenantId)
        assertEquals(Status.FAILED, tx.status)
        assertEquals(ErrorCode.EXPIRED.name, tx.errorCode)
        assertTrue(tx.requiresApproval)
        assertNull(tx.approved)

        val balance = balanceDao.findByAccountId(USER_ID).get()
        assertEquals(200000.0, balance.amount)

        val balance2 = balanceDao.findByAccountId(100L).get()
        assertEquals(100000.0, balance2.amount)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream).publish(eq(EventURN.TRANSACTION_FAILED.urn), payload.capture())
        assertEquals(TransactionType.TRANSFER.name, payload.firstValue.type)
        assertEquals(tx.id, payload.firstValue.transactionId)
    }

    @Test
    public fun notPending() {
        // WHEN
        url = "http://localhost:$port/v1/transactions/901/approve"
        val ex = assertThrows<HttpClientErrorException> {
            rest.getForEntity(url, Any::class.java)
        }

        // THEN
        assertEquals(409, ex.rawStatusCode)

        val error = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java).error
        assertEquals(ErrorURN.TRANSACTION_NOT_PENDING.urn, error.code)

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    public fun noPendingApproval() {
        // WHEN
        url = "http://localhost:$port/v1/transactions/902/approve"
        val ex = assertThrows<HttpClientErrorException> {
            rest.getForEntity(url, Any::class.java)
        }

        // THEN
        assertEquals(409, ex.rawStatusCode)

        val error = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java).error
        assertEquals(ErrorURN.NO_APPROVAL_REQUIRED.urn, error.code)

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    public fun notTransfer() {
        // WHEN
        url = "http://localhost:$port/v1/transactions/903/approve"
        val ex = assertThrows<HttpClientErrorException> {
            rest.getForEntity(url, Any::class.java)
        }

        // THEN
        assertEquals(409, ex.rawStatusCode)

        val error = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java).error
        assertEquals(ErrorURN.TRANSACTION_NOT_TRANSFER.urn, error.code)

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    public fun illegalTenant() {
        // WHEN
        url = "http://localhost:$port/v1/transactions/990/approve"
        val ex = assertThrows<HttpClientErrorException> {
            rest.getForEntity(url, Any::class.java)
        }

        // THEN
        assertEquals(403, ex.rawStatusCode)

        val error = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java).error
        assertEquals(ErrorURN.ILLEGAL_TENANT_ACCESS.urn, error.code)

        verify(eventStream, never()).publish(any(), any())
    }

    @Test
    public fun illegalApprover() {
        // WHEN
        url = "http://localhost:$port/v1/transactions/991/approve"
        val ex = assertThrows<HttpClientErrorException> {
            rest.getForEntity(url, Any::class.java)
        }

        // THEN
        assertEquals(403, ex.rawStatusCode)

        val error = ObjectMapper().readValue(ex.responseBodyAsString, ErrorResponse::class.java).error
        assertEquals(ErrorURN.ILLEGAL_APPROVER.urn, error.code)

        verify(eventStream, never()).publish(any(), any())
    }
}
