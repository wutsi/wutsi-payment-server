package com.wutsi.platform.payment.service

import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.core.util.URN
import com.wutsi.platform.payment.dao.AccountRepository
import com.wutsi.platform.payment.dao.GatewayRepository
import com.wutsi.platform.payment.dao.UserRepository
import com.wutsi.platform.payment.entity.AccountEntity
import com.wutsi.platform.payment.entity.AccountType.LIABILITY
import com.wutsi.platform.payment.entity.AccountType.REVENUE
import com.wutsi.platform.payment.entity.GatewayEntity
import com.wutsi.platform.payment.entity.UserEntity
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.stereotype.Service

@Service
class AccountService(
    private val userDao: UserRepository,
    private val accountDao: AccountRepository,
    private val gatewayDao: GatewayRepository,
) {
    fun findUserAccount(userId: Long, tenant: Tenant): AccountEntity {
        // User
        val user = userDao.findById(userId)
            .orElseGet {
                userDao.save(
                    UserEntity(
                        id = userId,
                    )
                )
            }

        var account = user.accounts.find { it.tenantId == tenant.id }
        if (account == null) {
            account = accountDao.save(
                AccountEntity(
                    type = LIABILITY,
                    tenantId = tenant.id,
                    currency = tenant.currency,
                    name = URN.of(type = "account", domain = "payment", name = "user:${user.id}").value
                )
            )
            user.accounts.add(account)
            userDao.save(user)
        }
        return account!!
    }

    fun findGatewayAccount(paymentMethod: PaymentMethod, tenant: Tenant): AccountEntity {
        val gateway = gatewayDao.findByCodeIgnoreCase(paymentMethod.provider)
            .orElseGet {
                gatewayDao.save(
                    GatewayEntity(
                        code = paymentMethod.provider,
                    )
                )
            }

        var account = gateway.accounts.find { it.tenantId == tenant.id }
        if (account == null) {
            account = accountDao.save(
                AccountEntity(
                    type = REVENUE,
                    tenantId = tenant.id,
                    currency = tenant.currency,
                    name = URN.of(type = "account", domain = "payment", name = "gateway:${paymentMethod.provider}").value
                )
            )
            gateway.accounts.add(account)
        }
        return account!!
    }

    fun updateBalance(account: AccountEntity, amount: Double): AccountEntity {
        account.balance += amount
        accountDao.save(account)
        return account
    }
}
