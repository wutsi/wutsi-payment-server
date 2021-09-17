package com.wutsi.platform.payment.service.job

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.service.event.EventURN
import com.wutsi.platform.payment.service.event.PayoutRequestedEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.jdbc.Sql

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/PayoutJob.sql"])
internal class PayoutJobTest {
    @Autowired
    lateinit var job: PayoutJob

    @MockBean
    lateinit var eventStream: EventStream

    @Test
    fun run() {
        job.run()

        val payload = argumentCaptor<PayoutRequestedEvent>()
        verify(eventStream, times(2)).enqueue(
            eq(EventURN.PAYOUT_REQUESTED.urn),
            payload.capture()
        )

        val accountIds = payload.allValues.sortedBy { it.accountId }.map { it.accountId }
        assertEquals(2, accountIds.size)
        assertEquals(1, accountIds[0])
        assertEquals(2, accountIds[1])
    }
}
