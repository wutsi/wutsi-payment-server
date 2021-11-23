package com.wutsi.platform.payment.delegate

import com.wutsi.platform.payment.dto.Balance
import com.wutsi.platform.payment.entity.BalanceEntity

fun BalanceEntity.toBalance() = Balance(
    id = this.id ?: -1,
    amount = this.amount,
    currency = this.currency,
    created = this.created,
    userId = this.userId
)
