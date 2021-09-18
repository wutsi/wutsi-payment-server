package com.wutsi.platform.payment.service

import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.core.Status.PENDING
import com.wutsi.platform.payment.dao.ChargeRepository
import com.wutsi.platform.payment.dao.PayoutRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType.CHARGE
import com.wutsi.platform.payment.entity.TransactionType.FEES
import com.wutsi.platform.payment.entity.TransactionType.PAYOUT
import com.wutsi.platform.payment.service.event.ChargeEventPayload
import com.wutsi.platform.payment.service.event.EventURN
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import javax.transaction.Transactional

@Service
class TransactionService(
    private val chargeDao: ChargeRepository,
    private val payoutDao: PayoutRepository,
    private val txDao: TransactionRepository,
    private val eventStream: EventStream,
    private val gatewayProvider: GatewayProvider,
    private val feesCalculator: FeesCalculator
) {
    companion object {
        const val FEES_ACCOUNT_ID = -100L
        private val LOGGER = LoggerFactory.getLogger(TransactionService::class.java)
    }

    @Transactional
    fun onChargeSuccessful(id: String) {
        val charge = chargeDao.findById(id).get()
        try {
            val fees = feesCalculator.compute(charge)
            val net = charge.amount - fees

            txDao.saveAll(
                listOf(
                    TransactionEntity(
                        referenceId = id,
                        type = CHARGE,
                        accountId = charge.merchantId,
                        description = charge.description,
                        amount = net,
                        currency = charge.currency,
                        created = charge.created,
                        paymentMethodProvider = charge.paymentMethodProvider
                    ),
                    TransactionEntity(
                        referenceId = id,
                        type = FEES,
                        accountId = FEES_ACCOUNT_ID,
                        amount = fees,
                        currency = charge.currency,
                        created = charge.created,
                        paymentMethodProvider = charge.paymentMethodProvider
                    )
                )
            )
        } catch (ex: DataIntegrityViolationException) {
            LOGGER.warn("This transaction was already recoded", ex)
        }
    }

    @Transactional
    fun onChargePending(id: String) {
        val charge = chargeDao.findById(id).get()
        val gateway = gatewayProvider.get(charge.paymentMethodProvider)
        try {
            val response = gateway.getPayment(charge.gatewayTransactionId)
            if (response.status == Status.SUCCESSFUL) {
                LOGGER.info("The charge is now SUCCESSFUL")
                charge.status = response.status
                charge.financialTransactionId = response.financialTransactionId
                chargeDao.save(charge)

                eventStream.enqueue(EventURN.CHARGE_SUCCESSFUL.urn, ChargeEventPayload(id))
            } else if (response.status == PENDING) {
                LOGGER.info("The charge is still PENDING")
            }
        } catch (ex: PaymentException) {
            LOGGER.info("The charge is now FAILED. error=${ex.error}")

            charge.status = Status.FAILED
            charge.errorCode = ex.error.code
            charge.supplierErrorCode = ex.error.supplierErrorCode
            chargeDao.save(charge)
        }
    }

    @Transactional
    fun onPayoutSuccessful(id: String) {
        val payout = payoutDao.findById(id).get()
        try {
            txDao.save(
                TransactionEntity(
                    referenceId = id,
                    type = PAYOUT,
                    accountId = payout.accountId,
                    description = payout.description,
                    amount = payout.amount,
                    currency = payout.currency,
                    created = payout.created,
                    paymentMethodProvider = payout.paymentMethodProvider
                )
            )
        } catch (ex: DataIntegrityViolationException) {
            LOGGER.warn("This transaction was already recoded", ex)
        }
    }
}
