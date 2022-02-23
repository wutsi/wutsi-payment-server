package com.wutsi.platform.payment.event

enum class EventURN(val urn: String) {
    TRANSACTION_SUCCESSFUL("urn:wutsi:event:payment:transaction-successful"),
    TRANSACTION_FAILED("urn:wutsi:event:payment:transaction-failed"),
    TRANSACTION_PENDING("urn:wutsi:event:payment:transaction-pending"),
}
