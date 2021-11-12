package com.wutsi.platform.payment.service

import com.wutsi.platform.core.security.WutsiPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class SecurityManager {
    fun currentUserId() = currentPrincipal().id.toLong()

    private fun currentPrincipal(): WutsiPrincipal =
        SecurityContextHolder.getContext().authentication.principal as WutsiPrincipal

//    fun canAccessPhone(account: AccountEntity): Boolean =
//        isOwner(account) || hasAuthority(PERMISSION_USER_PHONE)
//
//    fun canAccessPaymentMethodDetails(payment: PaymentMethodEntity): Boolean =
//        isOwner(payment.account) || hasAuthority(PERMISSION_PAYMENT_DETAILS)
//
//    private fun isOwner(account: AccountEntity): Boolean {
//        val authentication = SecurityContextHolder.getContext().authentication
//        val principal = authentication.principal
//        return principal is WutsiPrincipal &&
//            (account.id.toString() == principal.id || principal.admin)
//    }
//
//    private fun hasAuthority(authority: String): Boolean {
//        val authentication = SecurityContextHolder.getContext().authentication
//        val found = authentication?.authorities?.find { it.authority.equals(authority) }
//        if (found != null)
//            return true
//
//        val principal = authentication.principal
//        return principal is WutsiPrincipal && principal.admin
//    }
}
