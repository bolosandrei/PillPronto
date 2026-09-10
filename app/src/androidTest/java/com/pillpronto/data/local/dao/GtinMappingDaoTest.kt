package com.pillpronto.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pillpronto.data.local.PillProntoDatabase
import com.pillpronto.data.local.entity.GtinMappingEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Teste Room pe baza de date in-memory — verifica DAO-ul independent de Hilt/app. */
@RunWith(AndroidJUnit4::class)
class GtinMappingDaoTest {

    private lateinit var db: PillProntoDatabase
    private lateinit var dao: GtinMappingDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, PillProntoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.gtinMappingDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // Nume de test FARA spatii (camelCase/underscore, ca NomenclatureDaoTest) — nu backtick cu
    // spatii ca in testele unitare: aici genereaza o clasa lambda (runTest {}) cu spatii in nume,
    // respinsa de D8 la impachetarea DEX (minSdk=26 < versiunea DEX care permite asta). Gasit prin
    // rulare reala connectedDebugAndroidTest, nu doar teoretic.

    @Test
    fun findByGtin_emptyTable_returnsNull() = runTest {
        assertNull(dao.findByGtin("05901234123457"))
    }

    @Test
    fun upsertAndFindByGtin_roundTrip() = runTest {
        dao.upsert(GtinMappingEntity(gtin = "05901234123457", codCim = "W43451001", confirmedAt = 1000L))

        val found = dao.findByGtin("05901234123457")

        assertEquals("W43451001", found?.codCim)
        assertEquals(1000L, found?.confirmedAt)
    }

    @Test
    fun upsert_sameGtin_replacesWithoutDuplicating() = runTest {
        dao.upsert(GtinMappingEntity(gtin = "05901234123457", codCim = "W43451001", confirmedAt = 1000L))
        dao.upsert(GtinMappingEntity(gtin = "05901234123457", codCim = "W99999999", confirmedAt = 2000L))

        val found = dao.findByGtin("05901234123457")

        assertEquals("W99999999", found?.codCim)
        assertEquals(2000L, found?.confirmedAt)
    }

    @Test
    fun insertSeedBatch_doesNotOverwriteExisting() = runTest {
        dao.upsert(GtinMappingEntity(gtin = "05901234123457", codCim = "W43451001", confirmedAt = 1000L))

        dao.insertSeedBatch(listOf(GtinMappingEntity(gtin = "05901234123457", codCim = "W99999999", confirmedAt = 0L)))

        val found = dao.findByGtin("05901234123457")
        assertEquals("W43451001", found?.codCim)
        assertEquals(1000L, found?.confirmedAt)
    }

    @Test
    fun insertSeedBatch_fillsNewGtin() = runTest {
        dao.insertSeedBatch(listOf(GtinMappingEntity(gtin = "05901234123457", codCim = "W43451001", confirmedAt = 0L)))

        val found = dao.findByGtin("05901234123457")
        assertEquals("W43451001", found?.codCim)
    }

    @Test
    fun upsertAll_overwritesLocalGuessWithSharedCatalog() = runTest {
        dao.upsert(GtinMappingEntity(gtin = "05901234123457", codCim = "W_GRESIT", confirmedAt = 1000L))

        dao.upsertAll(listOf(GtinMappingEntity(gtin = "05901234123457", codCim = "W_CORECT", confirmedAt = 2000L)))

        val found = dao.findByGtin("05901234123457")
        assertEquals("W_CORECT", found?.codCim)
    }
}
