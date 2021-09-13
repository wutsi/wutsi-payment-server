package com.wutsi.platform.payment.callback

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController

@RestController
public class MTNController {
    @GetMapping("/mtn/callback")
    fun get() {
    }

    @PostMapping("/mtn/callback")
    fun post() {
    }

    @PutMapping("/mtn/callback")
    fun put() {
    }
}
