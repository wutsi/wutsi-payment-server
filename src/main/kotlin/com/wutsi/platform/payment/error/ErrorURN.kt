package com.wutsi.platform.payment.error

enum class ErrorURN(val urn: String) {
    BALANCE_NOT_FOUND("urn:wutsi:error:payment:balance-not-found"),
    TRANSACTION_FAILED("urn:wutsi:error:payment:transaction-failed"),
    TRANSACTION_NOT_FOUND("urn:wutsi:error:payment:transaction-not-found"),
    PAYMENT_REQUEST_NOT_FOUND("urn:wutsi:error:payment:payment-request-not-found"),
    CURRENCY_NOT_SUPPORTED("urn:wutsi:error:payment:currency-not-supported"),
    USER_NOT_ACTIVE("urn:wutsi:error:payment:user-not-found"),
    RECIPIENT_NOT_ACTIVE("urn:wutsi:error:payment:recipient-not-found"),
    RECIPIENT_NOT_VALID("urn:wutsi:error:payment:recipient-not-valid"),
    ORDER_NOT_VALID("urn:wutsi:error:payment:order-not-valid"),
    AMOUNT_NOT_VALID("urn:wutsi:error:payment:amount-not-valid"),
    ILLEGAL_TRANSACTION_ACCESS("urn:wutsi:error:payment:illegal-transaction-access"),
    ILLEGAL_TENANT_ACCESS("urn:wutsi:error:account:illegal-tenant-access"),
    ILLEGAL_PAYMENT_REQUEST_ACCESS("urn:wutsi:error:account:illegal-payment-request-access"),
    SELF_TRANSACTION_ERROR("urn:wutsi:error:account:self-transaction-error"),
    RESTRICTED_TO_BUSINESS_ACCOUNT("urn:wutsi:error:account:restricted-to-business-account"),
    ILLEGAL_APPROVER("urn:wutsi:error:account:illegal-approver"),
    TRANSACTION_NOT_PENDING("urn:wutsi:error:account:transaction-not-pending"),
    NO_APPROVAL_REQUIRED("urn:wutsi:error:account:no-approval-required"),
    TRANSACTION_NOT_TRANSFER("urn:wutsi:error:account:transaction-not-transfer"),
}
