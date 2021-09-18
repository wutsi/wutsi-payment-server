package com.wutsi.platform.payment.util

import com.wutsi.platform.core.util.URN

enum class ErrorURN(val urn: String) {
    ACCOUNT_NOT_ACTIVE(URN.of("error", "payment", "account-not-active").value),
    APPLICATION_NOT_ACTIVE(URN.of("error", "payment", "application-not-active").value),
    CHARGE_NOT_FOUND(URN.of("error", "payment", "charge-not-found").value),
    PAYMENT_METHOD_NOT_SUPPORTED(URN.of("error", "payment", "payment-method-not-supported").value),

    PAYOUT_NOT_FOUND(URN.of("error", "payment", "payout-not-found").value),
    PAYOUT_NO_PAYMENT_METHOD_FOUND(URN.of("error", "payment", "payout-no-payment-method-found").value),
    PAYOUT_AMOUNT_BELOW_THRESHOLD(URN.of("error", "payment", "payout-amount-below-threshold").value),

    TRANSACTION_FAILED(URN.of("error", "payment", "transaction-failed").value)
}
