package com.wutsi.platform.payment.service.event

import com.wutsi.platform.core.util.URN

enum class EventURN(val urn: String) {
    BALANCE_UPDATE_REQUESTED(URN.of("event", "payment", "balance-update-requested").value),

    CHARGE_SUCCESSFUL(URN.of("event", "payment", "charge-successful").value),
    CHARGE_PENDING(URN.of("event", "payment", "charge-pending").value),
    CHARGE_FAILED(URN.of("event", "payment", "charge-failed").value),
    PAYOUT_SUCCESSFUL(URN.of("event", "payment", "payout-successful").value),
    PAYOUT_PENDING(URN.of("event", "payment", "payout-pending").value),
    PAYOUT_FAILED(URN.of("event", "payment", "payout-failed").value)
}
