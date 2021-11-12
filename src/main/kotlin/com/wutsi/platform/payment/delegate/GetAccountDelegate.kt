package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.payment.dto.GetAccountResponse
import org.springframework.stereotype.Service
import kotlin.Long

@Service
public class GetAccountDelegate() {
    public fun invoke(id: Long): GetAccountResponse {
        TODO()
    }
}
