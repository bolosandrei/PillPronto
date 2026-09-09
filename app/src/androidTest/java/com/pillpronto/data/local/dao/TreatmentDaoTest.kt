package com.pillpronto.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pillpronto.data.local.PillProntoDatabase
import com.pillpronto.data.local.entity.TreatmentEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Teste Room pe baza de date in-memory — verifica DAO-ul independent de Hilt/app. */
@RunWith(AndroidJUnit4::class)
class TreatmentDaoTest {

    private lateinit var db: PillProntoDatabase
    private lateinit var dao: TreatmentDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, PillProntoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.treatmentDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun treatment(name: String, active: Boolean = true, asNeeded: Boolean = false) = TreatmentEntity(
        patientProfileId = "test-patient",
        medicationName = name,
        dosage = "1 doză",
        timesCsv = "08:00",
        startDate = "2026-01-01",
        endDate = null,
        active = active,
        asNeeded = asNeeded,
        remoteId = java.util.UUID.randomUUID().toString(),
        updatedAt = 0L
    )

    @Test
    fun upsert_and_getById_returnsInsertedTreatment() = runTest {
        val id = dao.upsert(treatment("Paracetamol"))

        val loaded = dao.getById(id)

        assertEquals("Paracetamol", loaded?.medicationName)
    }

    @Test
    fun observeAll_ordersByActiveThenName() = runTest {
        dao.upsert(treatment("Zolpidem", active = false))
        dao.upsert(treatment("Ibuprofen", active = true))
        dao.upsert(treatment("Amoxicilină", active = true))

        val all = dao.observeAll().first()

        // active DESC, medicationName ASC -> ambele active (alfabetic) inaintea celui inactiv.
        assertEquals(listOf("Amoxicilină", "Ibuprofen", "Zolpidem"), all.map { it.medicationName })
    }

    @Test
    fun getActive_excludesInactiveTreatments() = runTest {
        dao.upsert(treatment("Activ", active = true))
        dao.upsert(treatment("Inactiv", active = false))

        val active = dao.getActive()

        assertEquals(listOf("Activ"), active.map { it.medicationName })
    }

    @Test
    fun deleteById_removesTreatment() = runTest {
        val id = dao.upsert(treatment("De șters"))

        dao.deleteById(id)

        assertNull(dao.getById(id))
    }
}
