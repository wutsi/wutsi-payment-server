package com.wutsi.platform.payment.service.job

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.service.event.EventURN
import com.wutsi.platform.payment.service.event.PayoutEventPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.jdbc.Sql

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/PendingPayoutJob.sql"])
internal class PendingPayoutJobTest {
    @Autowired
    lateinit var job: PendingPayoutJob

    @MockBean
    lateinit var eventStream: EventStream

    @Test
    fun run() {
        job.run()

        val payload = argumentCaptor<PayoutEventPayload>()
        verify(eventStream, times(2)).enqueue(
            eq(EventURN.PAYOUT_PENDING.urn),
            payload.capture()
        )

        val payoutIds = payload.allValues.sortedBy { it.payoutId }.map { it.payoutId }
        assertEquals(2, payoutIds.size)
        assertEquals("200", payoutIds[0])
        assertEquals("201", payoutIds[1])
    }
}
