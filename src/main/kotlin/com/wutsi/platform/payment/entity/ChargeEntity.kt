package com.wutsi.platform.payment.entity

import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.PaymentMethodProvider.PAYMENT_METHOD_PROVDER_INVALID
import com.wutsi.platform.payment.PaymentMethodType
import com.wutsi.platform.payment.PaymentMethodType.PAYMENT_METHOD_TYPE_INVALID
import com.wutsi.platform.payment.core.ErrorCode
import com.wutsi.platform.payment.core.Status
import java.time.OffsetDateTime
import javax.persistence.Entity
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "T_CHARGE")
data class ChargeEntity(
    @Id
    val id: String = "",
    val merchantId: Long = -1,
    val customerId: Long = -1,
    val userId: Long = -1,
    val applicationId: Long = -1,
    val paymentMethodToken: String = "",

    @Enumerated
    val paymentMethodType: PaymentMethodType = PAYMENT_METHOD_TYPE_INVALID,

    @Enumerated
    val paymentMethodProvider: PaymentMethodProvider = PAYMENT_METHOD_PROVDER_INVALID,

    val externalId: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val currency: String = "",

    @Enumerated
    val status: Status = Status.STATUS_UNKNOWN,

    @Enumerated
    val errorCode: ErrorCode? = null,
    val supplierErrorCode: String? = null,
    val gatewayTransactionId: String = "",
    val created: OffsetDateTime = OffsetDateTime.now()
)
