package com.pillpronto.data.local.gtinmapping

import android.content.Context
import androidx.core.content.edit
import com.pillpronto.data.local.dao.GtinMappingDao
import com.pillpronto.data.local.entity.GtinMappingEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

private const val ASSET_NAME = "gtin_mappings_seed.tsv"
private const val EXPECTED_FIELD_COUNT = 2

// Incrementeaza cand adaugi randuri noi in gtin_mappings_seed.tsv la o sesiune viitoare — un
// import cu SEED_VERSION mai mare decat cea deja rulata (stocata in prefs) reia importul (safe:
// insertSeedBatch e IGNORE, randurile deja prezente raman neatinse).
private const val SEED_VERSION = 1
private const val PREFS_NAME = "pillpronto_seed_flags"
private const val KEY_GTIN_SEED_VERSION = "gtin_mappings_seed_version"

/** Parseaza o linie TSV "gtin\tcodCim" -> GtinMappingEntity, sau null daca linia e malformata —
 * functie pura, testabila unitar fara Room/Android. `confirmedAt = 0L` marcheaza randul ca
 * provenit din seed (nu dintr-o confirmare reala a userului la un scan). */
fun parseSeedLine(line: String): GtinMappingEntity? {
    val fields = line.split("\t")
    if (fields.size < EXPECTED_FIELD_COUNT) return null
    val gtin = fields[0].trim()
    val codCim = fields[1].trim()
    if (gtin.isBlank() || codCim.isBlank()) return null
    return GtinMappingEntity(gtin = gtin, codCim = codCim, confirmedAt = 0L)
}

/** Importa un set inițial de mapari GTIN->Cod CIM confirmate offline de dezvoltator (Faza 2b-i,
 * ecranul `AssociateGtinScreen`), livrate ca asset in APK — analog NomenclatureImporter, dar cu o
 * diferenta esentiala de gating: spre deosebire de Nomenclator (o singura sursa, un singur
 * import), `gtin_mappings` mai creste organic din confirmarile userilor reali la scanarile lor.
 * Gating pe `count() > 0` (ca la Nomenclator) ar insemna ca, daca userul confirma o mapare
 * inaintea acestui import, seed-ul intreg ar fi sarit silentios pt. totdeauna. In loc de asta,
 * un flag VERSIONAT in SharedPreferences + insert cu IGNORE — permite livrarea progresiva a
 * seed-ului in actualizari viitoare ale aplicatiei, fara sa suprascrie niciodata o mapare deja
 * existenta (nici a userului, nici dintr-un import anterior). */
class GtinMappingSeedImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: GtinMappingDao
) {
    suspend fun importIfNeeded() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_GTIN_SEED_VERSION, 0) >= SEED_VERSION) return

        val entities = mutableListOf<GtinMappingEntity>()
        context.assets.open(ASSET_NAME).use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
                lines.forEach { line -> parseSeedLine(line)?.let { entities.add(it) } }
            }
        }
        if (entities.isNotEmpty()) dao.insertSeedBatch(entities)
        prefs.edit { putInt(KEY_GTIN_SEED_VERSION, SEED_VERSION) }
    }
}
