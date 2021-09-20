package com.wutsi.platform.payment.service

import com.wutsi.platform.account.dto.Account
import com.wutsi.platform.core.security.SubjectType
import com.wutsi.platform.core.security.WutsiPrincipal
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class SecurityManager {
    fun checkOwnership(account: Account) {
        if (account.id != currentUserId())
            throw AccessDeniedException("User not owner")
    }

    fun currentUserId(): Long? {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        return if (principal is WutsiPrincipal && principal.type == SubjectType.USER)
            principal.id.toLong()
        else
            null
    }
}
