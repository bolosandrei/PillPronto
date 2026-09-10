package com.pillpronto.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pillpronto.data.local.nomenclature.NomenclatureDao
import com.pillpronto.data.local.nomenclature.NomenclatureDatabase
import com.pillpronto.data.local.nomenclature.NomenclatureEntity
import com.pillpronto.data.local.nomenclature.NomenclatureFtsEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Teste Room in-memory + FTS reale pt. `NomenclatureRepositoryImpl` — in special fallback-ul
 * fuzzy (Levenshtein), care are nevoie de comportamentul FTS4 real (nu poate fi simulat cu un
 * fake), vezi CLAUDE.md — "Algocalnin citit prin OCR in loc de Algocalmin". */
@RunWith(AndroidJUnit4::class)
class NomenclatureRepositoryImplTest {

    private lateinit var db: NomenclatureDatabase
    private lateinit var dao: NomenclatureDao
    private lateinit var repository: NomenclatureRepositoryImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NomenclatureDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.nomenclatureDao()
        repository = NomenclatureRepositoryImpl(dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entry(codCim: String, denumire: String, concentratie: String = "", forma: String = "") = NomenclatureEntity(
        codCim = codCim,
        denumireComerciala = denumire,
        dci = "",
        formaFarmaceutica = forma, concentratie = concentratie, firmaProducatoare = "", firmaDetinatoare = "",
        codAtc = "", actiuneTerapeutica = "", prescriptie = "", nrDataAmbalajApp = "", ambalaj = "",
        volumAmbalaj = "", valabilitateAmbalaj = "", bulina = "", diez = "", stea = "",
        triunghi = "", dreptunghi = "", dataActualizare = ""
    )

    private suspend fun insert(vararg entities: NomenclatureEntity) {
        val fts = entities.map { NomenclatureFtsEntity(it.codCim, it.denumireComerciala, it.dci) }
        dao.insertAll(entities.toList(), fts)
    }

    // Nume de test FARA spatii — vezi comentariul din GtinMappingDaoTest.kt (D8/DEX respinge
    // clasele lambda generate de runTest {} cu spatii in nume la minSdk=26).

    @Test
    fun search_exactMatch_unchanged() = runTest {
        insert(entry("A1", "ALGOCALMIN 500mg", concentratie = "500mg"))

        val results = repository.search("algocalmin")

        assertEquals(1, results.size)
        assertEquals("ALGOCALMIN 500mg", results.first().denumireComerciala)
    }

    @Test
    fun search_ocrTypo_findsCorrectProductViaFuzzyFallback() = runTest {
        insert(entry("A1", "ALGOCALMIN 500mg", concentratie = "500mg"))

        // "n" in loc de "m" la mijlocul cuvantului — cautarea exacta (prefix literal) nu gaseste
        // nimic, fallback-ul fuzzy trebuie sa gaseasca oricum ALGOCALMIN.
        val results = repository.search("ALGOCALNIN")

        assertEquals(1, results.size)
        assertEquals("ALGOCALMIN 500mg", results.first().denumireComerciala)
    }

    @Test
    fun search_completelyDifferentWord_returnsEmpty() = runTest {
        insert(entry("A1", "ALGOCALMIN 500mg"))

        val results = repository.search("IBUPROFEN")

        assertTrue(results.isEmpty())
    }

    @Test
    fun search_fuzzyFallback_respectsProductDedup() = runTest {
        // Doua ambalaje diferite ale aceluiasi produs (Cod CIM diferit, restul identic).
        insert(
            entry("A1", "ALGOCALMIN 500mg", concentratie = "500mg", forma = "COMPR."),
            entry("A2", "ALGOCALMIN 500mg", concentratie = "500mg", forma = "COMPR.")
        )

        val results = repository.search("ALGOCALNIN")

        assertEquals(1, results.size)
    }
}
