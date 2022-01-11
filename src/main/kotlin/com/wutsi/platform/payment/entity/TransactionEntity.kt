package com.wutsi.platform.payment.entity

import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.core.Status
import java.time.OffsetDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "T_TRANSACTION")
data class TransactionEntity(
    @Id
    val id: String? = null,

    val tenantId: Long = -1,
    val accountId: Long = -1,
    val recipientId: Long? = null,
    val type: TransactionType = TransactionType.UNKNOWN,
    val paymentMethodToken: String? = null,
    val paymentMethodProvider: PaymentMethodProvider? = null,
    val description: String? = null,
    var amount: Double = 0.0,
    var fees: Double = 0.0,
    var net: Double = 0.0,
    val currency: String = "",

    @Enumerated
    var status: Status = Status.UNKNOWN,
    var gatewayTransactionId: String? = null,
    var financialTransactionId: String? = null,
    var errorCode: String? = null,
    var supplierErrorCode: String? = null,
    val created: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "payment_request_fk")
    val paymentRequestId: String? = null,

    var feesToSender: Boolean = false,
    var business: Boolean = false,
    var retail: Boolean = false,
)
