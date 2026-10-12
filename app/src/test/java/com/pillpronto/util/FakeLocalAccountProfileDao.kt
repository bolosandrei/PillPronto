package com.pillpronto.util

import com.pillpronto.data.local.dao.LocalAccountProfileDao
import com.pillpronto.data.local.entity.LocalAccountProfileEntity

class FakeLocalAccountProfileDao : LocalAccountProfileDao {
    val rows = mutableMapOf<String, LocalAccountProfileEntity>()

    override suspend fun getByUserId(userId: String): LocalAccountProfileEntity? = rows[userId]

    override suspend fun insert(entity: LocalAccountProfileEntity) {
        rows[entity.userId] = entity
    }
}
