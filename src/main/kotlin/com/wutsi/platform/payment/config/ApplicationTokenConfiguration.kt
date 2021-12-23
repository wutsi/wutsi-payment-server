package com.wutsi.platform.notification.config

import com.wutsi.platform.notification.service.ApplicationTokenInitializer
import org.springframework.context.annotation.Configuration
import javax.annotation.PostConstruct

@Configuration
class ApplicationTokenConfiguration(
    private val initializer: ApplicationTokenInitializer
) {
    @PostConstruct
    fun init() {
        initializer.init()
    }
}
