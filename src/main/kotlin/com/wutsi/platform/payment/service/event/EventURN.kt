package com.wutsi.platform.payment.service.event

import com.wutsi.platform.core.util.URN

enum class EventURN(val urn: String) {
    CHARGE_SUCCESSFUL(URN.of("event", "payment", "charge-successful").value),
    CHARGE_PENDING(URN.of("event", "payment", "charge-pending").value),
    BALANCE_UPDATE_REQUESTED(URN.of("event", "payment", "balance-update-requested").value),
    PAYOUT_REQUESTED(URN.of("event", "payment", "payout-requested").value)
}
