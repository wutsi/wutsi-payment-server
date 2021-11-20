package com.wutsi.platform.payment.delegate

import com.wutsi.platform.payment.dto.Account
import com.wutsi.platform.payment.entity.AccountEntity

fun AccountEntity.toAccount() = Account(
    id = this.id!!,
    type = this.type.name,
    balance = this.balance,
    currency = this.currency,
    created = this.created,
    name = this.name
)
