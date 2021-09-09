package com.wutsi.platform.payment.service

import com.wutsi.platform.core.error.Error
import com.wutsi.platform.core.error.Parameter
import com.wutsi.platform.core.error.ParameterType
import com.wutsi.platform.core.error.exception.NotFoundException
import com.wutsi.platform.payment.util.ErrorURN
import com.wutsi.platform.security.WutsiSecurityApi
import com.wutsi.platform.security.dto.Application
import org.springframework.stereotype.Service

@Service
class SecurityService(
    private val api: WutsiSecurityApi
) {
    fun findApplication(id: Long, parameterName: String? = null, parameterType: ParameterType? = null): Application {
        val app = api.getApplication(id).application
        if (!app.active)
            throw NotFoundException(
                error = Error(
                    code = ErrorURN.APPLICATION_NOT_ACTIVE.urn,
                    parameter = parameterName?.let {
                        Parameter(
                            name = it,
                            value = id,
                            type = parameterType
                        )
                    }
                )
            )
        return app
    }
}
