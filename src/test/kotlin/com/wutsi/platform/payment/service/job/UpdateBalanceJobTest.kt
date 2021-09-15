package com.wutsi.platform.payment.service.job

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.service.event.AccountEventPayload
import com.wutsi.platform.payment.service.event.EventURN
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

        val payload = argumentCaptor<AccountEventPayload>()
        verify(eventStram, times(2)).enqueue(eq(EventURN.BALANCE_UPDATE_REQUESTED.urn), payload.capture())

        val accountIds = payload.allValues.map { it.accountId }.sorted()
        assertEquals(2L, accountIds[0])
        assertEquals(3L, accountIds[1])
    }
}
