package com.pillpronto.data.local.nomenclature

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface NomenclatureDao {

    // IGNORE, nu ABORT (default) — Nomenclatorul ANMDMR real contine cateva randuri cu Cod CIM
    // duplicat (calitate discutabila a datelor sursa, confirmat empiric la import pe device: fara
    // asta, tot importul esua cu UNIQUE constraint failed). Pastram prima aparitie, ignoram restul.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntities(entities: List<NomenclatureEntity>)

    @Insert
    suspend fun insertFts(entities: List<NomenclatureFtsEntity>)

    /** Import batched: ambele tabele populate in aceeasi tranzactie, din aceleasi randuri (vezi
     * NomenclatureImporter) — mentine FTS-ul sincron cu tabelul principal, fara triggere SQL. */
    @Transaction
    suspend fun insertAll(entities: List<NomenclatureEntity>, ftsEntities: List<NomenclatureFtsEntity>) {
        insertEntities(entities)
        insertFts(ftsEntities)
    }

    @Query("SELECT COUNT(*) FROM nomenclature")
    suspend fun count(): Int

    /** Potrivire prefix pe fiecare token din query (vezi NomenclatureRepositoryImpl — token* AND
     * intre tokenuri, comportamentul implicit FTS4). */
    @Query("SELECT codCim FROM nomenclature_fts WHERE nomenclature_fts MATCH :ftsQuery LIMIT :limit")
    suspend fun searchCodCim(ftsQuery: String, limit: Int): List<String>

    @Query("SELECT * FROM nomenclature WHERE codCim IN (:codCims)")
    suspend fun getByCodCims(codCims: List<String>): List<NomenclatureEntity>
}
