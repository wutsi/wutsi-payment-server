package com.wutsi.platform.payment.entity

import java.time.OffsetDateTime
import javax.persistence.Entity
import javax.persistence.FetchType.LAZY
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.JoinTable
import javax.persistence.ManyToMany
import javax.persistence.Table

@Entity
@Table(name = "T_USER")
data class UserEntity(
    @Id
    val id: Long? = null,

    @ManyToMany(fetch = LAZY)
    @JoinTable(
        name = "T_USER_ACCOUNT",
        joinColumns = arrayOf(JoinColumn(name = "user_fk")),
        inverseJoinColumns = arrayOf(JoinColumn(name = "account_fk"))
    )
    val accounts: MutableList<AccountEntity> = mutableListOf(),

    val created: OffsetDateTime = OffsetDateTime.now(),
)
