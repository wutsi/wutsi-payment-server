package com.wutsi.platform.payment.entity

import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodProvider.UNKNOWN
import com.wutsi.platform.payment.PaymentMethodType
import com.wutsi.platform.payment.core.ErrorCode
import com.wutsi.platform.payment.core.Status
import java.time.OffsetDateTime
import javax.persistence.Entity
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "T_PAYOUT")
data class PayoutEntity(
    @Id
    val id: String = "",
    val accountId: Long = -1,
    val userId: Long? = null,
    val paymentMethodToken: String = "",

    @Enumerated
    val paymentMethodType: PaymentMethodType = PaymentMethodType.UNKNOWN,

    @Enumerated
    val paymentMethodProvider: PaymentMethodProvider = UNKNOWN,

    val description: String? = null,
    val amount: Double = 0.0,
    val currency: String = "",

    @Enumerated
    var status: Status = Status.UNKNOWN,

    @Enumerated
    var errorCode: ErrorCode? = null,
    var supplierErrorCode: String? = null,
    var gatewayTransactionId: String = "",
    var financialTransactionId: String? = null,
    val created: OffsetDateTime = OffsetDateTime.now(),
    val updated: OffsetDateTime = OffsetDateTime.now()
)
