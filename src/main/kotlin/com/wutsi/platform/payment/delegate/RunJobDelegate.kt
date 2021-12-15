package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.Parameter
import com.wutsi.platform.core.error.ParameterType
import com.wutsi.platform.core.error.exception.BadRequestException
import com.wutsi.platform.payment.dto.RunJobResponse
import com.wutsi.platform.payment.error.ErrorURN
import com.wutsi.platform.payment.job.PendingCashinJob
import com.wutsi.platform.payment.job.PendingCashoutJob
import org.springframework.stereotype.Service
import java.util.UUID

@Service
public class RunJobDelegate(
    private val pendingCashinJob: PendingCashinJob,
    private val pendingCashoutJob: PendingCashoutJob
) {
    public fun invoke(name: String): RunJobResponse {
        if (name == "pending-cashin")
            pendingCashinJob.run()
        else if (name == "pending-cashout")
            pendingCashoutJob.run()
        else
            throw BadRequestException(
                error = Error(
                    code = ErrorURN.INVALID_JOB.urn,
                    parameter = Parameter(
                        name = "name",
                        value = name,
                        type = ParameterType.PARAMETER_TYPE_PATH
                    )
                )
            )

        return RunJobResponse(id = UUID.randomUUID().toString())
    }
}
