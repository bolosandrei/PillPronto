package com.pillpronto.data.local.nomenclature

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

private const val EXPECTED_FIELD_COUNT = 20
// Fisierul sursa in repo e "nomenclator.tsv.gz" (comprimat, ~845KB in git) — dar Android Gradle
// Plugin DECOMPRIMEAZA automat orice asset cu sufix .gz la impachetare si ii scoate extensia
// (comportament documentat AAPT/AGP pentru fisiere "deja comprimate"). In APK-ul instalat, asset-ul
// exista ca "nomenclator.tsv", deja necomprimat (~10.6MB) — confirmat empiric (unzip pe APK-ul
// debug), NU citim cu GZIPInputStream aici, am incerca sa decomprimam ceva deja decomprimat.
private const val ASSET_NAME = "nomenclator.tsv"
private const val BATCH_SIZE = 2000

/** Parseaza o linie TSV din assets/nomenclator.tsv -> NomenclatureEntity, sau null daca linia
 * e malformata (camp esential lipsa: codCim gol, sau prea putine coloane) — functie pura,
 * testabila unitar fara Room/Android. Coloanele sunt generate de scripts/convert-nomenclator.ps1,
 * in ordinea fixa documentata acolo (Cod CIM, Denumire comerciala, DCI, ...). */
fun parseNomenclatureLine(line: String): NomenclatureEntity? {
    val fields = line.split("\t")
    if (fields.size < EXPECTED_FIELD_COUNT) return null
    val codCim = fields[0].trim()
    if (codCim.isBlank()) return null
    return NomenclatureEntity(
        codCim = codCim,
        denumireComerciala = fields[1],
        dci = fields[2],
        formaFarmaceutica = fields[3],
        concentratie = fields[4],
        firmaProducatoare = fields[5],
        firmaDetinatoare = fields[6],
        codAtc = fields[7],
        actiuneTerapeutica = fields[8],
        prescriptie = fields[9],
        nrDataAmbalajApp = fields[10],
        ambalaj = fields[11],
        volumAmbalaj = fields[12],
        valabilitateAmbalaj = fields[13],
        bulina = fields[14],
        diez = fields[15],
        stea = fields[16],
        triunghi = fields[17],
        dreptunghi = fields[18],
        dataActualizare = fields[19]
    )
}

/** Importa Nomenclatorul din assets/nomenclator.tsv.gz in NomenclatureDatabase, o singura data
 * (verifica `count() > 0` — daca dataset-ul se actualizeaza vreodata, bump-ul de `version` din
 * NomenclatureDatabase + fallbackToDestructiveMigration() goleste tabelele si declanseaza automat
 * un reimport, fara nevoie de un mecanism separat de "versiune de dataset").
 *
 * Tot importul (toate loturile) ruleaza intr-o SINGURA tranzactie (`withTransaction`) — daca
 * worker-ul e omorat/anulat la mijloc, nimic nu ramane commis partial. Fara asta, un import
 * intrerupt ar lasa `count() > 0` (cateva mii de randuri) si `importIfNeeded()` ar crede pentru
 * totdeauna ca importul e deja complet, fara sa mai reincerce niciodata restul. */
class NomenclatureImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: NomenclatureDatabase,
    private val dao: NomenclatureDao
) {
    suspend fun importIfNeeded() {
        if (dao.count() > 0) return
        Log.i(TAG, "Import Nomenclator pornit...")
        val entities = mutableListOf<NomenclatureEntity>()
        context.assets.open(ASSET_NAME).use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
                lines.forEach { line -> parseNomenclatureLine(line)?.let { entities.add(it) } }
            }
        }
        database.withTransaction {
            entities.chunked(BATCH_SIZE).forEach { batch ->
                val fts = batch.map { NomenclatureFtsEntity(it.codCim, it.denumireComerciala, it.dci) }
                dao.insertAll(batch, fts)
            }
        }
        Log.i(TAG, "Import Nomenclator terminat: ${entities.size} randuri")
    }

    private companion object {
        const val TAG = "NomenclatureImporter"
    }
}
