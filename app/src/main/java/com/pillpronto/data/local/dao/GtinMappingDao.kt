package com.pillpronto.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pillpronto.data.local.entity.GtinMappingEntity

@Dao
interface GtinMappingDao {
    @Query("SELECT * FROM gtin_mappings WHERE gtin = :gtin LIMIT 1")
    suspend fun findByGtin(gtin: String): GtinMappingEntity?

    // REPLACE, nu IGNORE: un GTIN poate fi reconfirmat spre alt Cod CIM (userul corecteaza o
    // asociere gresita anterioara) — ultima confirmare a userului castiga.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GtinMappingEntity)

    // IGNORE, nu REPLACE: seed-ul livrat cu aplicatia (GtinMappingSeedImporter) NU trebuie sa
    // poata suprascrie niciodata o mapare deja existenta — nici a userului, nici a unui import
    // anterior — doar sa completeze golurile.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeedBatch(entities: List<GtinMappingEntity>)
}
