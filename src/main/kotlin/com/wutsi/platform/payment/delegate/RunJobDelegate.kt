package com.wutsi.platform.payment.`delegate`

import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.Parameter
import com.wutsi.platform.core.error.ParameterType
import com.wutsi.platform.core.error.exception.BadRequestException
import com.wutsi.platform.core.logging.KVLogger
import com.wutsi.platform.payment.dto.RunJobResponse
import com.wutsi.platform.payment.error.ErrorURN
import com.wutsi.platform.payment.job.PendingCashinJob
import com.wutsi.platform.payment.job.PendingCashoutJob
import org.springframework.stereotype.Service
import java.util.UUID

@Service
public class RunJobDelegate(
    private val pendingCashinJob: PendingCashinJob,
    private val pendingCashoutJob: PendingCashoutJob,
    private val logger: KVLogger
) {
    public fun invoke(name: String): RunJobResponse {
        var count: Int? = null

        logger.add("job", name)
        try {
            if (name == "pending-cashin")
                count = pendingCashinJob.run()
            else if (name == "pending-cashout")
                count = pendingCashoutJob.run()
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
        } finally {
            logger.add("count", count)
        }
    }
}
