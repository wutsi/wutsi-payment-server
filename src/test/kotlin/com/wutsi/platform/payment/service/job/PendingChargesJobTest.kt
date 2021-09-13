package com.wutsi.platform.payment.service.job

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.service.event.ChargeEventPayload
import com.wutsi.platform.payment.service.event.EventURN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.jdbc.Sql

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/PendingChargesJob.sql"])
internal class PendingChargesJobTest {
    @Autowired
    lateinit var job: PendingChargesJob

    @MockBean
    lateinit var eventStream: EventStream

    @Test
    fun run() {
        job.run()

        val payload = argumentCaptor<ChargeEventPayload>()
        verify(eventStream, times(3)).enqueue(
            eq(EventURN.CHARGE_PENDING.urn),
            payload.capture()
        )

        assertEquals("100", payload.firstValue.chargeId)
        assertEquals("101", payload.secondValue.chargeId)
        assertEquals("102", payload.thirdValue.chargeId)
    }
}
