package com.wutsi.platform.payment.event

import com.wutsi.platform.core.util.URN

enum class EventURN(val urn: String) {
    CHARGE_SUCCESSFUL(URN.of("event", "payment", "charge-successful").value),
    CHARGE_PENDING(URN.of("event", "payment", "charge-pending").value)
}