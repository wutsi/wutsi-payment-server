package com.wutsi.platform.payment.job

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.core.ErrorCode
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.endpoint.AbstractSecuredController
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.event.TransactionEventPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.jdbc.Sql

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/PendingTransferToExpireJob.sql"])
internal class PendingTransferToExpireJobTest : AbstractSecuredController() {
    @Autowired
    private lateinit var job: PendingTransferToExpireJob

    @Autowired
    private lateinit var dao: TransactionRepository

    @MockBean
    private lateinit var eventStream: EventStream


    @Test
    fun run() {
        // WHEN
        job.run()

        // THEN
        assertEquals(Status.PENDING, dao.findById("100").get().status)

        val tx = dao.findById("101").get()
        assertEquals(Status.FAILED, tx.status)
        assertEquals(ErrorCode.EXPIRED.name, tx.errorCode)

        assertEquals(Status.SUCCESSFUL, dao.findById("102").get().status)
        assertEquals(Status.FAILED, dao.findById("103").get().status)

        assertEquals(Status.PENDING, dao.findById("200").get().status)
        assertEquals(Status.PENDING, dao.findById("300").get().status)

        val payload = argumentCaptor<TransactionEventPayload>()
        verify(eventStream, times(1)).publish(eq(EventURN.TRANSACTION_FAILED.urn), payload.capture())
        assertEquals("101", payload.firstValue.transactionId)
    }


    @Test
    fun getJobName() {
        assertEquals("pending-transfer-to-expire", job.getJobName())
    }
}
