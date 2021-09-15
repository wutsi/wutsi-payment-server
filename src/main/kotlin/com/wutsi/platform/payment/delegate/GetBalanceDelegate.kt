package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.payment.dto.GetBalanceResponse
import com.wutsi.platform.payment.service.BalanceService
import org.springframework.stereotype.Service

@Service
public class GetBalanceDelegate(
    private val service: BalanceService
) {
    fun invoke(accountId: Long): GetBalanceResponse =
        GetBalanceResponse(
            balance = service.getBalance(accountId)
        )
}
