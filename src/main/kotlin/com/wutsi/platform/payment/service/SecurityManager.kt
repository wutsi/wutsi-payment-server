package com.wutsi.platform.payment.service

import com.wutsi.platform.account.dto.Account
import com.wutsi.platform.core.security.WutsiPrincipal
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import java.security.Principal

@Service
class SecurityManager {
    fun checkOwnership(account: Account) {
        if (account.id != currentUserId())
            throw AccessDeniedException("User not the owner of payment method")
    }

    fun currentUserId(): Long {
        val principal = SecurityContextHolder.getContext().authentication.principal as Principal
        return if (principal is WutsiPrincipal)
            principal.id.toLong()
        else
            -1
    }
}
