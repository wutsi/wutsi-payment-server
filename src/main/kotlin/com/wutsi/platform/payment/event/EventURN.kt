package com.wutsi.platform.payment.event

enum class EventURN(val urn: String) {
    TRANSACTION_SUCCESSFULL("urn:wutsi:event:payment:transaction-successful"),
    TRANSACTION_FAILED("urn:wutsi:event:payment:transaction-failed"),
}
