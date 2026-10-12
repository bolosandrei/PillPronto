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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val PROFILE_A = "test-patient"
private const val PROFILE_B = "test-patient-b"

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

    private fun treatment(
        name: String,
        active: Boolean = true,
        asNeeded: Boolean = false,
        patientProfileId: String = PROFILE_A,
        dirty: Boolean = false
    ) = TreatmentEntity(
        patientProfileId = patientProfileId,
        medicationName = name,
        dosage = "1 doză",
        timesCsv = "08:00",
        startDate = "2026-01-01",
        endDate = null,
        active = active,
        asNeeded = asNeeded,
        remoteId = java.util.UUID.randomUUID().toString(),
        updatedAt = 0L,
        dirty = dirty
    )

    @Test
    fun upsert_and_getById_returnsInsertedTreatment() = runTest {
        val id = dao.upsert(treatment("Paracetamol"))

        val loaded = dao.getById(id)

        assertEquals("Paracetamol", loaded?.medicationName)
    }

    @Test
    fun observeAllForProfile_ordersByActiveThenName() = runTest {
        dao.upsert(treatment("Zolpidem", active = false))
        dao.upsert(treatment("Ibuprofen", active = true))
        dao.upsert(treatment("Amoxicilină", active = true))

        val all = dao.observeAllForProfile(PROFILE_A).first()

        // active DESC, medicationName ASC -> ambele active (alfabetic) inaintea celui inactiv.
        assertEquals(listOf("Amoxicilină", "Ibuprofen", "Zolpidem"), all.map { it.medicationName })
    }

    @Test
    fun getActiveForProfile_excludesInactiveTreatments() = runTest {
        dao.upsert(treatment("Activ", active = true))
        dao.upsert(treatment("Inactiv", active = false))

        val active = dao.getActiveForProfile(PROFILE_A)

        assertEquals(listOf("Activ"), active.map { it.medicationName })
    }

    @Test
    fun deleteById_removesTreatment() = runTest {
        val id = dao.upsert(treatment("De șters"))

        dao.deleteById(id)

        assertNull(dao.getById(id))
    }

    // --- fix izolare date locale: query-urile scopate pe profil nu scurg date intre conturi ---

    @Test
    fun observeAllForProfile_nuAratTratamenteleAltuiProfil() = runTest {
        dao.upsert(treatment("Al lui A", patientProfileId = PROFILE_A))
        dao.upsert(treatment("Al lui B", patientProfileId = PROFILE_B))

        val forA = dao.observeAllForProfile(PROFILE_A).first()
        val forB = dao.observeAllForProfile(PROFILE_B).first()

        assertEquals(listOf("Al lui A"), forA.map { it.medicationName })
        assertEquals(listOf("Al lui B"), forB.map { it.medicationName })
    }

    @Test
    fun getActiveForProfile_nuAratTratamenteleAltuiProfil() = runTest {
        dao.upsert(treatment("Activ A", active = true, patientProfileId = PROFILE_A))
        dao.upsert(treatment("Activ B", active = true, patientProfileId = PROFILE_B))

        assertEquals(listOf("Activ A"), dao.getActiveForProfile(PROFILE_A).map { it.medicationName })
    }

    @Test
    fun getDirtyForProfile_nuAratRandurileMurdareAleAltuiProfil() = runTest {
        dao.upsert(treatment("Murdar A", patientProfileId = PROFILE_A, dirty = true))
        dao.upsert(treatment("Murdar B", patientProfileId = PROFILE_B, dirty = true))

        assertEquals(listOf("Murdar A"), dao.getDirtyForProfile(PROFILE_A).map { it.medicationName })
    }

    @Test
    fun hasAnyForProfile_adevaratDoarPentruProfilulCuDate() = runTest {
        dao.upsert(treatment("Doar A", patientProfileId = PROFILE_A))

        assertTrue(dao.hasAnyForProfile(PROFILE_A))
        assertFalse(dao.hasAnyForProfile(PROFILE_B))
    }
}
