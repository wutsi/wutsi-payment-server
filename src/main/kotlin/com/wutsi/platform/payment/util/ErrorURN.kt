package com.wutsi.platform.payment.util

import com.wutsi.platform.core.util.URN

enum class ErrorURN(val urn: String) {
    ACCOUNT_NOT_ACTIVE(URN.of("error", "payment", "account-not-active").value),
    APPLICATION_NOT_ACTIVE(URN.of("error", "payment", "application-not-active").value),
    CHARGE_NOT_FOUND(URN.of("error", "payment", "charge-not-found").value),
    TRANSACTION_FAILED(URN.of("error", "payment", "transaction-failed").value)
}
