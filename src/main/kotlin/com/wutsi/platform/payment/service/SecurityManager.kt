package com.wutsi.platform.payment.service

import com.wutsi.platform.core.security.WutsiPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class SecurityManager {
    fun currentUserId() = currentPrincipal().id.toLong()

    private fun currentPrincipal(): WutsiPrincipal =
        SecurityContextHolder.getContext().authentication.principal as WutsiPrincipal
}
