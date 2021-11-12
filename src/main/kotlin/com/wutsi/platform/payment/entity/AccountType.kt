package com.wutsi.platform.payment.entity

enum class AccountType(val increaseOnDebit: Boolean) {
    UNKNOWN(false),
    REVENUE(true),
    LIABILITY(false),
}
