package com.wutsi.platform.payment.service

import com.wutsi.platform.payment.dao.ChargeRepository
import com.wutsi.platform.payment.dao.PayoutRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType.CHARGE
import com.wutsi.platform.payment.entity.TransactionType.FEES
import com.wutsi.platform.payment.entity.TransactionType.PAYOUT
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import javax.transaction.Transactional

@Service
class TransactionService(
    private val chargeDao: ChargeRepository,
    private val payoutDao: PayoutRepository,
    private val txDao: TransactionRepository,
    private val feesCalculator: FeesCalculator
) {
    companion object {
        const val FEES_ACCOUNT_ID = -100L
        private val LOGGER = LoggerFactory.getLogger(TransactionService::class.java)
    }

    @Transactional
    fun onChargeSuccessful(chargeId: String) {
        val charge = chargeDao.findById(chargeId).get()
        val fees = feesCalculator.compute(charge)
        val net = charge.amount - fees

        txDao.saveAll(
            listOf(
                TransactionEntity(
                    referenceId = chargeId,
                    type = CHARGE,
                    accountId = charge.merchantId,
                    description = charge.description,
                    amount = net,
                    currency = charge.currency,
                    created = charge.created,
                    paymentMethodProvider = charge.paymentMethodProvider
                ),
                TransactionEntity(
                    referenceId = chargeId,
                    type = FEES,
                    accountId = FEES_ACCOUNT_ID,
                    amount = fees,
                    currency = charge.currency,
                    created = charge.created,
                    paymentMethodProvider = charge.paymentMethodProvider
                )
            )
        )
    }

    @Transactional
    fun onPayoutSuccessful(payoutId: String) {
        val payout = payoutDao.findById(payoutId).get()
        txDao.save(
            TransactionEntity(
                referenceId = payoutId,
                type = PAYOUT,
                accountId = payout.accountId,
                description = payout.description,
                amount = -payout.amount,
                currency = payout.currency,
                created = payout.created,
                paymentMethodProvider = payout.paymentMethodProvider
            )
        )
    }
}
