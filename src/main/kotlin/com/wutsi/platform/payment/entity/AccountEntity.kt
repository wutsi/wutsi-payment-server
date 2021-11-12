package com.wutsi.platform.payment.entity

import com.wutsi.platform.payment.entity.AccountType.UNKNOWN
import java.time.OffsetDateTime
import javax.persistence.Entity
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "T_ACCOUNT")
data class AccountEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Enumerated
    val type: AccountType = UNKNOWN,

    val tenantId: Long = -1,
    val name: String? = null,
    var balance: Double = 0.0,
    val currency: String = "",
    val created: OffsetDateTime = OffsetDateTime.now(),
)
