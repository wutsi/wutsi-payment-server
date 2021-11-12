package com.wutsi.platform.payment.dao

import com.wutsi.platform.payment.entity.AccountEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface AccountRepository : CrudRepository<AccountEntity, Long>
