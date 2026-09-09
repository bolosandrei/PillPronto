package com.pillpronto.data.local.nomenclature

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Teste Room pe baza de date in-memory pentru NomenclatureDatabase — verifica in special ca FTS4
 * gaseste dupa nume partial si e insensibil la diacritice (motivul principal pt. care s-a ales
 * FTS4 in loc de LIKE simplu, vezi CLAUDE.md sectiunea 7). */
@RunWith(AndroidJUnit4::class)
class NomenclatureDaoTest {

    private lateinit var db: NomenclatureDatabase
    private lateinit var dao: NomenclatureDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NomenclatureDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.nomenclatureDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entry(codCim: String, denumire: String, dci: String = "") = NomenclatureEntity(
        codCim = codCim,
        denumireComerciala = denumire,
        dci = dci,
        formaFarmaceutica = "", concentratie = "", firmaProducatoare = "", firmaDetinatoare = "",
        codAtc = "", actiuneTerapeutica = "", prescriptie = "", nrDataAmbalajApp = "", ambalaj = "",
        volumAmbalaj = "", valabilitateAmbalaj = "", bulina = "", diez = "", stea = "",
        triunghi = "", dreptunghi = "", dataActualizare = ""
    )

    private suspend fun insert(vararg entities: NomenclatureEntity) {
        val fts = entities.map { NomenclatureFtsEntity(it.codCim, it.denumireComerciala, it.dci) }
        dao.insertAll(entities.toList(), fts)
    }

    @Test
    fun search_findsByNamePrefix() = runTest {
        insert(
            entry("A1", "ASPIRINA 500mg"),
            entry("A2", "PARACETAMOL 500mg")
        )

        val results = dao.searchCodCim("aspir*", 20)

        assertEquals(listOf("A1"), results)
    }

    @Test
    fun search_isDiacriticInsensitive() = runTest {
        insert(entry("A1", "ASPIRINA 500mg", dci = "ACIDUM ACETYLSALICILIC"))

        // tokenizer-ul unicode61 remove_diacritics=2 normalizeaza atat la indexare cat si la
        // interogare — verificam ambele directii.
        val results = dao.searchCodCim("acetylsalicilic*", 20)

        assertEquals(listOf("A1"), results)
    }

    @Test
    fun search_noMatches_returnsEmptyList() = runTest {
        insert(entry("A1", "ASPIRINA 500mg"))

        val results = dao.searchCodCim("ibuprofen*", 20)

        assertTrue(results.isEmpty())
    }

    @Test
    fun getByCodCims_returnsFullRowForGivenCodes() = runTest {
        insert(entry("A1", "ASPIRINA 500mg", dci = "ACIDUM ACETYLSALICYLICUM"))

        val results = dao.getByCodCims(listOf("A1"))

        assertEquals(1, results.size)
        assertEquals("ACIDUM ACETYLSALICYLICUM", results.first().dci)
    }

    @Test
    fun count_reflectsInsertedRows() = runTest {
        assertEquals(0, dao.count())

        insert(entry("A1", "ASPIRINA 500mg"), entry("A2", "PARACETAMOL 500mg"))

        assertEquals(2, dao.count())
    }
}
