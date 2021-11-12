package com.wutsi.platform.payment.entity

import java.time.OffsetDateTime
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.OneToOne
import javax.persistence.Table

@Entity
@Table(name = "T_USER")
data class UserEntity(
    @Id
    val id: Long? = null,

    @OneToOne
    @JoinColumn(name = "account_fk")
    val account: AccountEntity = AccountEntity(),

    val created: OffsetDateTime = OffsetDateTime.now(),
)
