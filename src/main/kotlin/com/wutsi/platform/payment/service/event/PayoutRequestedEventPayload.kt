package com.wutsi.platform.payment.service.event

import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodProvider.UNKNOWN

data class PayoutRequestedEventPayload(
    val accountId: Long = -1,
    val paymentMethodProvider: PaymentMethodProvider = UNKNOWN
)
