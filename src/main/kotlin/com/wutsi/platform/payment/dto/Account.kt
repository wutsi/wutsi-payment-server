package com.wutsi.platform.payment.dto

import javax.validation.constraints.Size

public data class Account(
    public val id: Long = 0,
    public val name: String = "",
    public val balance: Double = 0.0,
    @get:Size(max = 3)
    public val currency: String = "",
    public val ownerType: String = ""
)
