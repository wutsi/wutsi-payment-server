package com.wutsi.platform.payment.util

enum class ErrorURN(val urn: String) {
    TRANSACTION_FAILED("urn:wutsi:error:payment:transaction-failed"),
    CURRENCY_NOT_SUPPORTED("urn:wutsi:error:payment:currency-not-supported"),
    ACCOUNT_NOT_FOUND("urn:wutsi:error:payment:account-not-found"),
}
