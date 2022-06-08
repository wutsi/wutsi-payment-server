package com.wutsi.platform.payment.event

import com.wutsi.platform.core.logging.KVLogger
import com.wutsi.platform.payment.Gateway
import com.wutsi.platform.payment.GatewayProvider
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.core.Money
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.delegate.CreateCashinDelegate
import com.wutsi.platform.payment.delegate.CreateCashoutDelegate
import com.wutsi.platform.payment.delegate.CreateChargeDelegate
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType
import com.wutsi.platform.payment.model.CreatePaymentResponse
import com.wutsi.platform.payment.model.CreateTransferResponse
import com.wutsi.platform.tenant.WutsiTenantApi
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TransactionEventHandler(
    private val dao: TransactionRepository,
    private val gatewayProvider: GatewayProvider,
    private val tenantApi: WutsiTenantApi,
    private val cashinDelegate: CreateCashinDelegate,
    private val cashoutDelegate: CreateCashoutDelegate,
    private val chargeDelegate: CreateChargeDelegate,
    private val logger: KVLogger,
) {
    @Transactional
    fun onSync(transactionId: String) {
        logger.add("transaction_id", transactionId)
        val tx = dao.findById(transactionId).orElse(null)
        val gateway = tx?.paymentMethodProvider?.let { gatewayProvider.get(it) }
            ?: return

        try {
            if (tx.status == Status.PENDING && tx.gatewayTransactionId != null)
                try {
                    when (tx.type) {
                        TransactionType.CHARGE -> syncCharge(tx, gateway)
                        TransactionType.CASHOUT -> syncCashout(tx, gateway)
                        TransactionType.CASHIN -> syncCashin(tx, gateway)
                        else -> {}
                    }
                } catch (ex: PaymentException) {
                    onError(tx, ex)
                }
        } finally {
            logger.add("transaction_type", tx.type)
            logger.add("transaction_status", tx.status)
            logger.add("provider", tx.paymentMethodProvider)
            logger.add("transaction_gateway_id", tx.gatewayTransactionId)
            logger.add("transaction_gateway_fees", tx.gatewayFees)
            logger.add("transaction_fees", tx.fees)
            logger.add("transaction_amount", tx.amount)
            logger.add("transaction_net", tx.net)
            logger.add("gateway", gateway::class.java.simpleName)
        }
    }

    private fun syncCashin(tx: TransactionEntity, gateway: Gateway) {
        val response = gateway.getPayment(tx.gatewayTransactionId!!)
        if (response.status == Status.SUCCESSFUL)
            onSuccess(tx, tx.gatewayTransactionId!!, response.financialTransactionId, response.status, response.fees)
    }

    private fun syncCashout(tx: TransactionEntity, gateway: Gateway) {
        val response = gateway.getTransfer(tx.gatewayTransactionId!!)
        if (response.status == Status.SUCCESSFUL)
            onSuccess(tx, tx.gatewayTransactionId!!, response.financialTransactionId, response.status, response.fees)
    }

    private fun syncCharge(tx: TransactionEntity, gateway: Gateway) {
        val response = gateway.getPayment(tx.gatewayTransactionId!!)
        if (response.status == Status.SUCCESSFUL)
            onSuccess(tx, tx.gatewayTransactionId!!, response.financialTransactionId, response.status, response.fees)
    }

    private fun onError(tx: TransactionEntity, ex: PaymentException) {
        when (tx.type) {
            TransactionType.CHARGE -> chargeDelegate.onError(tx, ex)
            TransactionType.CASHOUT -> cashoutDelegate.onError(tx, ex)
            TransactionType.CASHIN -> cashinDelegate.onError(tx, ex)
            else -> {}
        }
    }

    private fun onSuccess(
        tx: TransactionEntity,
        gatewayTransactionId: String,
        financialTransactionId: String?,
        status: Status,
        fees: Money
    ) {
        when (tx.type) {
            TransactionType.CHARGE -> chargeDelegate.onSuccess(
                tx = tx,
                tenant = tenantApi.getTenant(tx.tenantId).tenant,
                response = CreatePaymentResponse(
                    transactionId = gatewayTransactionId,
                    financialTransactionId = financialTransactionId,
                    status = status,
                    fees = fees
                )
            )

            TransactionType.CASHOUT -> cashoutDelegate.onSuccess(
                tx = tx,
                response = CreateTransferResponse(
                    transactionId = gatewayTransactionId ?: "-",
                    financialTransactionId = financialTransactionId,
                    status = status,
                    fees = fees
                )
            )

            TransactionType.CASHIN -> cashinDelegate.onSuccess(
                tx = tx,
                tenant = tenantApi.getTenant(tx.tenantId).tenant,
                response = CreatePaymentResponse(
                    transactionId = gatewayTransactionId ?: "-",
                    financialTransactionId = financialTransactionId,
                    status = status,
                    fees = fees
                )
            )
            else -> {}
        }
    }
}
