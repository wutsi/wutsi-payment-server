package com.wutsi.platform.payment.entity

import java.time.OffsetDateTime
import javax.persistence.Entity
import javax.persistence.FetchType.LAZY
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.JoinTable
import javax.persistence.ManyToMany
import javax.persistence.Table

@Entity
@Table(name = "T_GATEWAY")
data class GatewayEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val code: String = "",

    @ManyToMany(fetch = LAZY)
    @JoinTable(
        name = "T_GATEWAY_ACCOUNT",
        joinColumns = arrayOf(JoinColumn(name = "gateway_fk")),
        inverseJoinColumns = arrayOf(JoinColumn(name = "account_fk"))
    )
    val accounts: List<AccountEntity> = emptyList(),

    val created: OffsetDateTime = OffsetDateTime.now(),
)
