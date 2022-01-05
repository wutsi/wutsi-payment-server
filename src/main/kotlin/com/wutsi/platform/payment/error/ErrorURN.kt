package com.wutsi.platform.payment.error

enum class ErrorURN(val urn: String) {
    BALANCE_NOT_FOUND("urn:wutsi:error:payment:balance-not-found"),
    TRANSACTION_FAILED("urn:wutsi:error:payment:transaction-failed"),
    TRANSACTION_NOT_FOUND("urn:wutsi:error:payment:transaction-not-found"),
    PAYMENT_REQUEST_NOT_FOUND("urn:wutsi:error:payment:payment-request-not-found"),
    CURRENCY_NOT_SUPPORTED("urn:wutsi:error:payment:currency-not-supported"),
    USER_NOT_ACTIVE("urn:wutsi:error:payment:user-not-found"),
    RECIPIENT_NOT_ACTIVE("urn:wutsi:error:payment:recipient-not-found"),
    ILLEGAL_TRANSACTION_ACCESS("urn:wutsi:error:payment:illegal-transaction-access"),
    ILLEGAL_TENANT_ACCESS("urn:wutsi:error:account:illegal-tenant-access"),
    SELF_TRANSACTION_ERROR("urn:wutsi:error:account:self-transaction-error"),
    RESTRICTED_TO_BUSINESS_ACCOUNT("urn:wutsi:error:account:restricted-to-business-account"),
}
