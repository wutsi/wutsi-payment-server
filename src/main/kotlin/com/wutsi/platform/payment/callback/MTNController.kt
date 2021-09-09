package com.wutsi.platform.payment.callback

import com.wutsi.platform.core.logging.KVLogger
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@RestController
public class MTNController(private val logger: KVLogger) {
    @PostMapping("/v1/mtn/callback")
    fun post(request: HttpServletRequest, response: HttpServletResponse) {
    }

    @PutMapping("/v1/mtn/callback")
    fun put(request: HttpServletRequest, response: HttpServletResponse) {
    }
}
