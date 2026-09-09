package com.pillpronto.util

import com.pillpronto.data.local.dao.PendingRemoteDeleteDao
import com.pillpronto.data.local.entity.PendingRemoteDeleteEntity

class FakePendingRemoteDeleteDao : PendingRemoteDeleteDao {

    private val rows = mutableListOf<PendingRemoteDeleteEntity>()

    override suspend fun insert(entity: PendingRemoteDeleteEntity) {
        if (rows.none { it.remoteId == entity.remoteId }) rows += entity
    }

    override suspend fun getAll(): List<PendingRemoteDeleteEntity> = rows.toList()

    override suspend fun deleteByRemoteId(remoteId: String) {
        rows.removeAll { it.remoteId == remoteId }
    }
}
