package com.wutsi.platform.payment.service

import com.wutsi.platform.core.stream.EventStream
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.core.Status.PENDING
import com.wutsi.platform.payment.dao.ChargeRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType.CHARGE
import com.wutsi.platform.payment.event.ChargeEventPayload
import com.wutsi.platform.payment.event.EventURN
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import javax.transaction.Transactional

@Service
class TransactionService(
    private val chargeDao: ChargeRepository,
    private val txDao: TransactionRepository,
    private val eventStream: EventStream,
    private val gatewayProvider: GatewayProvider,
    private val feesCalculator: FeesCalculator
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(TransactionService::class.java)
    }

    @Transactional
    fun onChargeSuccessful(id: String) {
        LOGGER.info("Recording new transaction")

        val charge = chargeDao.findById(id).get()
        try {
            val fees = feesCalculator.compute(charge)

            txDao.save(
                TransactionEntity(
                    id = id,
                    type = CHARGE,
                    fromAccountId = charge.customerId,
                    toAccountId = charge.merchantId,
                    currency = charge.currency,
                    created = OffsetDateTime.now(),
                    description = charge.description,
                    amount = charge.amount,
                    fees = fees,
                    net = charge.amount - fees
                )
            )
        } catch (ex: DataIntegrityViolationException) {
            LOGGER.warn("This transaction was already recoded", ex)
        }
    }

    @Transactional
    fun onChargePending(id: String) {
        val charge = chargeDao.findById(id).get()
        if (charge.status != PENDING) {
            LOGGER.warn("Handling the event ${EventURN.CHARGE_PENDING}, but charge.status=${charge.status}. Ignoring the event")
            return
        }

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
}
