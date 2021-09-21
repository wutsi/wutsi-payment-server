package com.wutsi.platform.payment.service

import com.wutsi.platform.core.logging.KVLogger
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.dao.BalanceRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dto.Balance
import com.wutsi.platform.payment.entity.BalanceEntity
import com.wutsi.platform.payment.entity.TransactionEntity
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Optional
import java.util.UUID
import javax.transaction.Transactional

@Service
public class BalanceService(
    private val dao: BalanceRepository,
    private val txDao: TransactionRepository,
    private val logger: KVLogger
) {
    fun getBalance(accountId: Long, paymentMethodProvider: PaymentMethodProvider): Balance {
        val balance = dao.findByAccountIdAndPaymentMethodProvider(accountId, paymentMethodProvider)
        val transactions = if (balance.isEmpty)
            txDao.findByAccountIdAndPaymentMethodProvider(accountId, paymentMethodProvider)
        else
            txDao.findByAccountIdAndPaymentMethodProviderAndCreatedGreaterThanEqual(
                accountId,
                paymentMethodProvider,
                toOffsetDateTime(balance.get().synced)
            )

        logger.add("account_id", accountId)
        logger.add("transaction_count", transactions.size)
        if (balance.isPresent) {
            logger.add("balance_synced", balance.get().synced)
            logger.add("balance_base", balance.get().amount)
            logger.add("balance_currency", balance.get().currency)
            logger.add("balance_provider", balance.get().paymentMethodProvider)
        }
        return Balance(
            accountId = accountId,
            paymentMethodProvider = paymentMethodProvider.name,
            currency = currency(balance, transactions),
            amount = amount(balance, transactions),
            synced = balance.map { it.synced }.orElse(LocalDate.now())
        )
    }

    fun getBalanceToPayout(): List<BalanceEntity> =
        dao.findByAmountGreaterThan(0.0)

    @Transactional
    fun update(accountId: Long, paymentMethodProvider: PaymentMethodProvider) {
        val opt = dao.findByAccountIdAndPaymentMethodProvider(accountId, paymentMethodProvider)
        val date = LocalDate.now()
        if (opt.isEmpty) {
            val transactions = txDao.findByAccountIdAndPaymentMethodProviderAndCreatedLessThan(
                accountId,
                paymentMethodProvider,
                toOffsetDateTime(date)
            )
            dao.save(
                BalanceEntity(
                    accountId = accountId,
                    paymentMethodProvider = paymentMethodProvider,
                    amount = amount(opt, transactions),
                    currency = currency(opt, transactions),
                    synced = date,
                    payoutId = UUID.randomUUID().toString()
                )
            )
        } else {
            val balance = opt.get()
            val transactions = txDao.findByAccountIdAndPaymentMethodProviderAndCreatedGreaterThanEqualAndCreatedLessThan(
                accountId,
                paymentMethodProvider,
                toOffsetDateTime(balance.synced),
                toOffsetDateTime(date)
            )
            balance.amount = amount(opt, transactions)
            balance.synced = date
            balance.payoutId = UUID.randomUUID().toString()
            dao.save(balance)
        }
    }

    private fun toOffsetDateTime(date: LocalDate): OffsetDateTime =
        date.atStartOfDay(ZoneId.of("UTC")).toOffsetDateTime()

    private fun currency(balance: Optional<BalanceEntity>, transactions: List<TransactionEntity>): String =
        if (balance.isPresent)
            balance.get().currency
        else if (transactions.isEmpty())
            ""
        else
            transactions[0].currency

    private fun amount(balance: Optional<BalanceEntity>, transactions: List<TransactionEntity>): Double =
        balance.map { it.amount }.orElse(0.0) + transactions.map { it.amount }.sum()
}
