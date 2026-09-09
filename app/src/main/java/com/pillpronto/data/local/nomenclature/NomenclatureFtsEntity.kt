package com.pillpronto.data.local.nomenclature

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

/** Tabel virtual FTS4 STANDALONE (nu "external content" legat de NomenclatureEntity) — populat
 * separat, cu aceleasi randuri, in aceeasi tranzactie de import (vezi NomenclatureImporter).
 * Decizie deliberata: FTS4 cu `contentEntity` ar cere triggere SQL manuale ca sa ramana
 * sincronizat cu tabelul sursa (Room nu le genereaza automat) — inutil aici, pentru ca datele
 * sunt scrise o singura data la import, niciodata actualizate ulterior. Un tabel FTS de sine
 * statator, cu propria copie a campurilor cautabile, e mai simplu si mai robust.
 * `remove_diacritics=2` — cautare insensibila la diacritice ("aspirina" gaseste "ASPIRINA"). */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, tokenizerArgs = ["remove_diacritics=2"])
@Entity(tableName = "nomenclature_fts")
data class NomenclatureFtsEntity(
    val codCim: String,
    val denumireComerciala: String,
    val dci: String
)
