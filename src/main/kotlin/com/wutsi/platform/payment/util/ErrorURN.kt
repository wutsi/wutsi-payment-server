package com.wutsi.platform.payment.util

import com.wutsi.platform.core.util.URN

enum class ErrorURN(val urn: String) {
    GATEWAY_NOT_SUPPORTED(URN.of("error", "payment", "gateway-not-supported").value),
    TRANSACTION_FAILED(URN.of("error", "payment", "transaction-failed").value),
    CURRENCY_NOT_SUPPORTED(URN.of("error", "payment", "currency-not-supported").value),
    ACCOUNT_NOT_FOUND(URN.of("error", "payment", "account-not-found").value),
    RECIPIENT_CANNOT_RECEIVE_TRANSFER(URN.of("error", "payment", "recipient-cannot-receive-transfer").value),
}
