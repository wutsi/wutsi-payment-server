package com.wutsi.platform.payment.entity

import com.wutsi.platform.payment.core.Status
import java.time.OffsetDateTime
import javax.persistence.Entity
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(name = "T_TRANSACTION")
data class TransactionEntity(
    @Id
    val id: String? = null,

    @ManyToOne()
    @JoinColumn(name = "account_fk")
    val account: AccountEntity = AccountEntity(),

    @ManyToOne()
    @JoinColumn(name = "gateway_fk")
    val gateway: GatewayEntity = GatewayEntity(),

    val type: TransactionType = TransactionType.UNKNOWN,
    val paymentMethodToken: String = "",
    val description: String? = null,
    val amount: Double = 0.0,
    val currency: String = "",
    var gatewayTransactionId: String? = null,
    var financialTransactionId: String? = null,
    var errorCode: String? = null,
    var supplierErrorCode: String? = null,

    val created: OffsetDateTime = OffsetDateTime.now(),

    @Enumerated
    var status: Status = Status.UNKNOWN
)
