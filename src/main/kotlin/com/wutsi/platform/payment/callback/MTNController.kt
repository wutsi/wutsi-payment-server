package com.wutsi.platform.payment.callback

import com.wutsi.platform.core.logging.KVLogger
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController

@RestController
public class MTNController(private val logger: KVLogger) {
    @GetMapping("/v1/mtn/callback")
    fun get() {
    }

    @PostMapping("/v1/mtn/callback")
    fun post() {
    }

    @PutMapping("/v1/mtn/callback")
    fun put() {
    }
}
