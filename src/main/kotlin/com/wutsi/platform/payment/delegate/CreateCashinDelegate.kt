package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.account.WutsiAccountApi
import com.wutsi.platform.account.dto.PaymentMethod
import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.Parameter
import com.wutsi.platform.core.error.ParameterType.PARAMETER_TYPE_PAYLOAD
import com.wutsi.platform.core.error.exception.BadRequestException
import com.wutsi.platform.core.logging.KVLogger
import com.wutsi.platform.core.util.URN
import com.wutsi.platform.payment.GatewayProvider
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.PaymentMethodProvider
import com.wutsi.platform.payment.core.Money
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.dao.AccountRepository
import com.wutsi.platform.payment.dao.GatewayRepository
import com.wutsi.platform.payment.dao.RecordRepository
import com.wutsi.platform.payment.dao.TransactionRepository
import com.wutsi.platform.payment.dao.UserRepository
import com.wutsi.platform.payment.dto.CreateCashinRequest
import com.wutsi.platform.payment.dto.CreateCashinResponse
import com.wutsi.platform.payment.entity.AccountEntity
import com.wutsi.platform.payment.entity.AccountType.LIABILITY
import com.wutsi.platform.payment.entity.AccountType.REVENUE
import com.wutsi.platform.payment.entity.GatewayEntity
import com.wutsi.platform.payment.entity.RecordEntity
import com.wutsi.platform.payment.entity.TransactionEntity
import com.wutsi.platform.payment.entity.TransactionType.CASHIN
import com.wutsi.platform.payment.entity.UserEntity
import com.wutsi.platform.payment.exception.TransactionException
import com.wutsi.platform.payment.model.CreatePaymentRequest
import com.wutsi.platform.payment.model.CreatePaymentResponse
import com.wutsi.platform.payment.model.Party
import com.wutsi.platform.payment.service.SecurityManager
import com.wutsi.platform.payment.service.TenantProvider
import com.wutsi.platform.payment.util.ErrorURN
import com.wutsi.platform.tenant.dto.Tenant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
class CreateCashinDelegate(
    private val securityManager: SecurityManager,
    private val accountApi: WutsiAccountApi,
    private val userDao: UserRepository,
    private val transactionDao: TransactionRepository,
    private val accountDao: AccountRepository,
    private val gatewayDao: GatewayRepository,
    private val recordDao: RecordRepository,
    private val tenantProvider: TenantProvider,
    private val gatewayProvider: GatewayProvider,
    private val logger: KVLogger
) {
    @Transactional(noRollbackFor = [TransactionException::class])
    fun invoke(request: CreateCashinRequest): CreateCashinResponse {
        // Tenant
        val tenant = tenantProvider.get()
        validate(request, tenant)

        // Gateway
        val paymentMethod = accountApi.getPaymentMethod(
            id = securityManager.currentUserId(),
            token = request.paymentMethodToken
        ).paymentMethod

        // Create transaction
        val tx = createTransaction(request, tenant)

        // Perform the transfer
        try {
            val response = performTransaction(tx, paymentMethod)
            if (response.status == Status.SUCCESSFUL) {
                onSuccess(tx, response, paymentMethod, tenant)
            } else {
                onPending(tx, response)
            }

            return CreateCashinResponse(
                id = tx.id!!,
                status = tx.status.name
            )
        } catch (ex: PaymentException) {
            onError(tx, ex)
            throw TransactionException(
                error = Error(
                    code = ErrorURN.TRANSACTION_FAILED.urn,
                    downstreamCode = ex.error.code.name,
                    data = mapOf(
                        "id" to tx.id!!
                    )
                )
            )
        }
    }

    private fun validate(request: CreateCashinRequest, tenant: Tenant) {
        if (request.currency != tenant.currency) {
            throw BadRequestException(
                error = Error(
                    code = ErrorURN.CURRENCY_NOT_SUPPORTED.urn,
                    parameter = Parameter(
                        type = PARAMETER_TYPE_PAYLOAD,
                        name = "currency",
                        value = request.currency
                    )
                )
            )
        }
    }

    private fun createTransaction(request: CreateCashinRequest, tenant: Tenant): TransactionEntity {
        val tx = transactionDao.save(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                paymentMethodToken = request.paymentMethodToken,
                type = CASHIN,
                amount = request.amount,
                currency = tenant.currency,
                status = Status.PENDING,
                created = OffsetDateTime.now(),
            )
        )
        logger.add("transaction_id", tx.id)

        return tx
    }

    private fun performTransaction(tx: TransactionEntity, paymentMethod: PaymentMethod): CreatePaymentResponse {
        val paymentGateway = gatewayProvider.get(PaymentMethodProvider.valueOf(paymentMethod.provider))
        val response = paymentGateway.createPayment(
            CreatePaymentRequest(
                payer = Party(
                    fullName = paymentMethod.ownerName,
                    phoneNumber = paymentMethod.phone!!.number
                ),
                amount = Money(tx.amount, tx.currency),
                externalId = tx.id!!,
                description = "Cashin",
                payerMessage = null
            )
        )
        logger.add("gateway_status", response.status)
        logger.add("gateway_transaction_id", response.transactionId)
        logger.add("gateway_financial_transaction_id", response.financialTransactionId)

        return response
    }

    private fun onError(tx: TransactionEntity, ex: PaymentException) {
        logger.add("gateway_error_code", ex.error.code)
        logger.add("gateway_supplier_error_code", ex.error.supplierErrorCode)

        tx.status = Status.FAILED
        tx.errorCode = ex.error.code.name
        tx.supplierErrorCode = ex.error.supplierErrorCode
        tx.gatewayTransactionId = ex.error.transactionId
        transactionDao.save(tx)
    }

    private fun onPending(tx: TransactionEntity, response: CreatePaymentResponse) {
        tx.status = Status.PENDING
        tx.gatewayTransactionId = response.transactionId
        transactionDao.save(tx)
    }

    private fun onSuccess(tx: TransactionEntity, response: CreatePaymentResponse, paymentMethod: PaymentMethod, tenant: Tenant) {
        tx.status = Status.SUCCESSFUL
        tx.gatewayTransactionId = response.transactionId
        tx.financialTransactionId = response.financialTransactionId
        transactionDao.save(tx)

        updateLedger(tx, paymentMethod, tenant)
    }

    private fun updateLedger(tx: TransactionEntity, paymentMethod: PaymentMethod, tenant: Tenant) {
        // Create records
        val userAccount = findUserAccount(securityManager.currentUserId(), tenant)
        val gatewayAccount = findGatewayAccount(paymentMethod, tenant)
        recordDao.saveAll(
            listOf(
                RecordEntity.increase(tx, userAccount, tx.amount),
                RecordEntity.increase(tx, gatewayAccount, tx.amount),
            )
        )

        // Update balance
        updateBalance(userAccount, tx.amount)
        updateBalance(gatewayAccount, tx.amount)
    }

    private fun findUserAccount(userId: Long, tenant: Tenant): AccountEntity {
        logger.add("user_id", userId)

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

    private fun findGatewayAccount(paymentMethod: PaymentMethod, tenant: Tenant): AccountEntity {
        logger.add("payment_method", paymentMethod)

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

    private fun updateBalance(account: AccountEntity, amount: Double): AccountEntity {
        account.balance += amount
        accountDao.save(account)

        logger.add("account_${account.id}_balance", amount)
        return account
    }
}
