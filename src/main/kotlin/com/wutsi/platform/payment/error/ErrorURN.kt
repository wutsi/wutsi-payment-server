package com.wutsi.platform.payment.error

enum class ErrorURN(val urn: String) {
    TRANSACTION_FAILED("urn:wutsi:error:payment:transaction-failed"),
    TRANSACTION_NOT_FOUND("urn:wutsi:error:payment:transaction-not-found"),
    CURRENCY_NOT_SUPPORTED("urn:wutsi:error:payment:currency-not-supported"),
    USER_NOT_ACTIVE("urn:wutsi:error:payment:user-not-found"),
    RECIPIENT_NOT_ACTIVE("urn:wutsi:error:payment:recipient-not-found"),
    OWNERSHIP_ERROR("urn:wutsi:error:payment:ownership-error"),
    INVALID_TENANT("urn:wutsi:error:payment:invalid-tenant"),
    INVALID_JOB("urn:wutsi:error:payment:invalid-job"),
}
