package com.wutsi.platform.payment.service

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
import javax.transaction.Transactional

@Service
public class BalanceService(
    private val dao: BalanceRepository,
    private val txDao: TransactionRepository
) {
    fun getBalance(accountId: Long): Balance {
        val balance = dao.findByAccountId(accountId)
        val transactions = if (balance.isEmpty)
            txDao.findByAccountId(accountId)
        else
            txDao.findByAccountIdAndCreatedGreaterThanEqual(accountId, toOffsetDateTime(balance.get().synced))

        return Balance(
            accountId = accountId,
            currency = currency(balance, transactions),
            amount = amount(balance, transactions),
            synced = balance.map { it.synced }.orElse(LocalDate.now())
        )
    }

    @Transactional
    fun update(merchantId: Long) {
        val opt = dao.findByAccountId(merchantId)
        val date = LocalDate.now()
        if (opt.isEmpty) {
            val transactions = txDao.findByAccountIdAndCreatedLessThan(merchantId, toOffsetDateTime(date))
            dao.save(
                BalanceEntity(
                    accountId = merchantId,
                    amount = amount(opt, transactions),
                    currency = currency(opt, transactions),
                    synced = date
                )
            )
        } else {
            val balance = opt.get()
            val transactions = txDao.findByAccountIdAndCreatedGreaterThanEqualAndCreatedLessThan(
                merchantId,
                toOffsetDateTime(balance.synced),
                toOffsetDateTime(date)
            )
            balance.amount = amount(opt, transactions)
            balance.synced = date
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
