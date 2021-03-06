package com.wutsi.platform.payment.delegate

import com.wutsi.platform.payment.dto.Balance
import com.wutsi.platform.payment.dto.Transaction
import com.wutsi.platform.payment.dto.TransactionSummary
import com.wutsi.platform.payment.entity.BalanceEntity
import com.wutsi.platform.payment.entity.TransactionEntity

fun BalanceEntity.toBalance() = Balance(
    id = this.id ?: -1,
    amount = this.amount,
    currency = this.currency,
    created = this.created,
    userId = this.accountId
)

fun TransactionEntity.toTransaction() = Transaction(
    id = this.id ?: "",
    recipientId = this.recipientId,
    type = this.type.name,
    created = this.created,
    accountId = this.accountId,
    tenantId = this.tenantId,
    currency = this.currency,
    gatewayTransactionId = this.gatewayTransactionId,
    amount = this.amount,
    errorCode = this.errorCode,
    supplierErrorCode = this.supplierErrorCode,
    paymentMethodProvider = this.paymentMethodProvider?.name,
    paymentMethodToken = this.paymentMethodToken,
    financialTransactionId = this.financialTransactionId,
    description = this.description,
    fees = this.fees,
    gatewayFees = this.gatewayFees,
    net = this.net,
    status = this.status.name,
    orderId = this.orderId,
    applyFeesToSender = this.applyFeesToSender,
)

fun TransactionEntity.toTransactionSummary() = TransactionSummary(
    id = this.id ?: "",
    recipientId = this.recipientId,
    type = this.type.name,
    created = this.created,
    accountId = this.accountId,
    currency = this.currency,
    amount = this.amount,
    paymentMethodToken = this.paymentMethodToken,
    paymentMethodProvider = this.paymentMethodProvider?.name,
    description = this.description,
    fees = this.fees,
    gatewayFees = this.gatewayFees,
    net = this.net,
    status = this.status.name,
    errorCode = this.errorCode,
    supplierErrorCode = this.supplierErrorCode,
    orderId = this.orderId,
    applyFeesToSender = this.applyFeesToSender
)
