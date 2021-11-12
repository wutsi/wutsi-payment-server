package com.wutsi.platform.payment.util

import com.wutsi.platform.core.util.URN

enum class ErrorURN(val urn: String) {
    GATEWAY_NOT_SUPPORTED(URN.of("error", "payment", "gateway-not-supported").value),
    TRANSACTION_FAILED(URN.of("error", "payment", "transaction-failed").value),
    CURRENCY_NOT_SUPPORTED(URN.of("error", "payment", "currency-not-supported").value),
}
