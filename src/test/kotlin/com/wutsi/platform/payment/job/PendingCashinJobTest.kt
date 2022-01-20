package com.wutsi.platform.payment.job

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.core.Error
import com.wutsi.platform.payment.core.ErrorCode
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.endpoint.AbstractSecuredController
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.model.GetPaymentResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.assertNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/PendingCashinJob.sql"])
internal class PendingCashinJobTest : AbstractSecuredController() {
    @Autowired
    private lateinit var job: PendingCashinJob

    @Autowired
    private lateinit var dao: TransactionRepository

    @MockBean
    private lateinit var eventStream: EventStream

    @Test
    fun getJobName() {
        assertEquals("pending-cashin", job.getJobName())
    }

    @Test
    fun succes() {
        // GIVEN
        val resp = GetPaymentResponse(
            status = Status.SUCCESSFUL,
            financialTransactionId = "financial-transaction-111",
        )
        doReturn(resp).whenever(mtnGateway).getPayment(any())
        doReturn(resp).whenever(omGateway).getPayment(any())

        // WHEN
        job.run()

        // THEN
        val tx1 = dao.findById("1").get()
        assertEquals(Status.SUCCESSFUL, tx1.status)
        assertEquals(resp.financialTransactionId, tx1.financialTransactionId)

        val tx11 = dao.findById("11").get()
        assertEquals(Status.SUCCESSFUL, tx11.status)
        assertEquals(resp.financialTransactionId, tx11.financialTransactionId)

        val tx111 = dao.findById("111").get()
        assertEquals(Status.SUCCESSFUL, tx111.status)
        assertEquals("fin-111", tx111.financialTransactionId)
        assertEquals("gw-111", tx111.gatewayTransactionId)

        verify(eventStream, times(2)).publish(eq(EventURN.TRANSACTION_SUCCESSFULL.urn), any())
    }

    @Test
    fun failure() {
        // GIVEN
        val ex = PaymentException(
            error = Error(
                code = ErrorCode.PAYMENT_NOT_APPROVED,
                transactionId = "xxx",
                supplierErrorCode = "yyy"
            )
        )
        doThrow(ex).whenever(mtnGateway).getPayment(any())

        // WHEN
        job.run()

        // THEN
        val tx1 = dao.findById("1").get()
        assertEquals(Status.FAILED, tx1.status)
        assertNull(tx1.financialTransactionId)
        assertEquals(ex.error.code.name, tx1.errorCode)
        assertEquals(ex.error.supplierErrorCode, tx1.supplierErrorCode)

        val tx11 = dao.findById("11").get()
        assertEquals(Status.FAILED, tx11.status)
        assertNull(tx11.financialTransactionId)
        assertEquals(ex.error.code.name, tx11.errorCode)
        assertEquals(ex.error.supplierErrorCode, tx11.supplierErrorCode)

        val tx111 = dao.findById("111").get()
        assertEquals(Status.SUCCESSFUL, tx111.status)
        assertEquals("fin-111", tx111.financialTransactionId)
        assertEquals("gw-111", tx111.gatewayTransactionId)

        verify(eventStream, times(2)).publish(eq(EventURN.TRANSACTION_FAILED.urn), any())
    }
}
