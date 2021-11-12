package com.wutsi.platform.payment.dao

import com.wutsi.platform.payment.entity.UserEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : CrudRepository<UserEntity, Long>
