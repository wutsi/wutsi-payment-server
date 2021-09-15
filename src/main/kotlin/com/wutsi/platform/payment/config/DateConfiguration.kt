package com.wutsi.platform.payment.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
public class DateConfiguration {
    @Bean
    fun clock(): Clock =
        Clock.systemUTC()
}
