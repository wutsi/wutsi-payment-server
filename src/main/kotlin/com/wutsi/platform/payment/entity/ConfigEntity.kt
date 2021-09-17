package com.wutsi.platform.payment.entity

import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodProvider.UNKNOWN
import javax.persistence.Entity
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "T_CONFIG")
data class ConfigEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = -1,

    @Enumerated
    val paymentMethodProvider: PaymentMethodProvider = UNKNOWN,

    val country: String = "",
    val payoutMinValue: Double = 0.0,
    val payoutMaxValue: Double = 0.0,
    val feesPercent: Double = 0.0,
    val feesValue: Double = 0.0,
)
