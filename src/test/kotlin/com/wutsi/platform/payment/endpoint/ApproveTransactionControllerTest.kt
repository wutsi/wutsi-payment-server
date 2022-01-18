package com.wutsi.platform.payment.endpoint

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import kotlin.Int

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ApproveTransactionControllerTest {
    @LocalServerPort
    public val port: Int = 0

    @Test
    public fun invoke() {
    }
}
