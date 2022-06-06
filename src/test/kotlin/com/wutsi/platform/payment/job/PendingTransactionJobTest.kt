package com.wutsi.platform.payment.job

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.endpoint.AbstractSecuredController
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.event.EventURN
import com.wutsi.platform.payment.event.TransactionEventPayload
import com.wutsi.platform.payment.model.GetTransferResponse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.jdbc.Sql

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/PendingTransactionJob.sql"])
internal class PendingTransactionJobTest : AbstractSecuredController() {
    @Autowired
    private lateinit var job: PendingTransactionJob

    @MockBean
    private lateinit var eventStream: EventStream

    @Test
    fun run() {
        // GIVEN
        val resp = GetTransferResponse(
            status = Status.SUCCESSFUL,
            financialTransactionId = "financial-transaction-111",
        )
        doReturn(resp).whenever(gateway).getTransfer(any())

        // WHEN
        job.run()

        // THEN
        verify(eventStream, times(4)).enqueue(any(), any())
        verify(eventStream).enqueue(
            EventURN.TRANSACTION_SYNC_REQUESTED.urn,
            TransactionEventPayload(transactionId = "100", type = TransactionType.CASHIN.name)
        )
        verify(eventStream).enqueue(
            EventURN.TRANSACTION_SYNC_REQUESTED.urn,
            TransactionEventPayload(transactionId = "101", type = TransactionType.CASHIN.name)
        )
        verify(eventStream).enqueue(
            EventURN.TRANSACTION_SYNC_REQUESTED.urn,
            TransactionEventPayload(transactionId = "200", type = TransactionType.CASHOUT.name)
        )
        verify(eventStream).enqueue(
            EventURN.TRANSACTION_SYNC_REQUESTED.urn,
            TransactionEventPayload(transactionId = "400", type = TransactionType.CHARGE.name)
        )
    }
}
