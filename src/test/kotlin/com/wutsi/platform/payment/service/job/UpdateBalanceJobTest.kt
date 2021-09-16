package com.wutsi.platform.payment.service.job

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.service.event.EventURN
import com.wutsi.platform.payment.service.event.UpdateBalanceEvent
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/UpdateBalanceJob.sql"])
internal class UpdateBalanceJobTest {
    @Autowired
    lateinit var job: UpdateBalanceJob

    @MockBean
    lateinit var eventStram: EventStream

    @Test
    fun run() {
        job.run()

        val payload = argumentCaptor<UpdateBalanceEvent>()
        verify(eventStram, times(3)).enqueue(eq(EventURN.BALANCE_UPDATE_REQUESTED.urn), payload.capture())

        val data = payload.allValues.sortedBy { it.accountId }
        assertEquals(2L, data[0].accountId)
        assertEquals(PaymentMethodProvider.MTN, data[1].paymentMethodProvider)

        assertEquals(3L, data[1].accountId)
        assertEquals(PaymentMethodProvider.MTN, data[1].paymentMethodProvider)

        assertEquals(3L, data[2].accountId)
        assertEquals(PaymentMethodProvider.ORANGE, data[2].paymentMethodProvider)
    }
}
