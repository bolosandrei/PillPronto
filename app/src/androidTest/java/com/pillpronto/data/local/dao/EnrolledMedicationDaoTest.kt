package com.pillpronto.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pillpronto.data.local.PillProntoDatabase
import com.pillpronto.data.local.entity.EnrolledMedicationEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Teste Room pe baza de date in-memory (Faza 4b) — nume de test fara spatii/puncte, ca
 * GtinMappingDaoTest (D8/DEX respinge nume de clase lambda cu spatii la minSdk=26). */
@RunWith(AndroidJUnit4::class)
class EnrolledMedicationDaoTest {

    private lateinit var db: PillProntoDatabase
    private lateinit var dao: EnrolledMedicationDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, PillProntoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.enrolledMedicationDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun findAll_emptyTable_returnsEmptyList() = runTest {
        assertTrue(dao.findAll().isEmpty())
    }

    @Test
    fun insertAndFindAll_roundTrip() = runTest {
        val embedding = byteArrayOf(1, 2, 3, 4)
        dao.insert(EnrolledMedicationEntity(codCim = "W43451001", embedding = embedding, capturedAt = 1000L))

        val all = dao.findAll()

        assertEquals(1, all.size)
        assertEquals("W43451001", all[0].codCim)
        assertArrayEquals(embedding, all[0].embedding)
        assertEquals(1000L, all[0].capturedAt)
    }

    @Test
    fun insert_sameCodCimMultipleTimes_keepsSeparateRows() = runTest {
        dao.insert(EnrolledMedicationEntity(codCim = "W43451001", embedding = byteArrayOf(1), capturedAt = 1000L))
        dao.insert(EnrolledMedicationEntity(codCim = "W43451001", embedding = byteArrayOf(2), capturedAt = 2000L))

        val all = dao.findAll()

        assertEquals(2, all.size)
    }
}
