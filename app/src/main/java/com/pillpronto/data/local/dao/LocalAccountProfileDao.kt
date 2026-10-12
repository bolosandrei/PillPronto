package com.pillpronto.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pillpronto.data.local.entity.LocalAccountProfileEntity

@Dao
interface LocalAccountProfileDao {
    @Query("SELECT * FROM local_account_profiles WHERE userId = :userId")
    suspend fun getByUserId(userId: String): LocalAccountProfileEntity?

    @Insert
    suspend fun insert(entity: LocalAccountProfileEntity)
}
