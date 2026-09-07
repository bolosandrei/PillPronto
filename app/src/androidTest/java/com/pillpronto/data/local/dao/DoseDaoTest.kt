package com.pillpronto.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pillpronto.data.local.PillProntoDatabase
import com.pillpronto.data.local.entity.DoseLogEntity
import com.pillpronto.data.local.entity.TreatmentEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DoseDaoTest {

    private lateinit var db: PillProntoDatabase
    private lateinit var treatmentDao: TreatmentDao
    private lateinit var doseDao: DoseDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, PillProntoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        treatmentDao = db.treatmentDao()
        doseDao = db.doseDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertTreatment(name: String = "Paracetamol"): Long =
        treatmentDao.upsert(
            TreatmentEntity(
                patientProfileId = "test-patient",
                medicationName = name,
                dosage = "500 mg",
                timesCsv = "08:00,20:00",
                startDate = "2026-01-01",
                endDate = null,
                active = true
            )
        )

    private fun dose(treatmentId: Long, scheduledAt: String, status: String = "PENDING") = DoseLogEntity(
        patientProfileId = "test-patient",
        treatmentId = treatmentId,
        scheduledAt = scheduledAt,
        status = status,
        takenAt = null
    )

    @Test
    fun observeForDate_joinsTreatmentInfoForMatchingDay() = runTest {
        val treatmentId = insertTreatment()
        doseDao.insertAll(
            listOf(
                dose(treatmentId, "2026-01-05T08:00:00"),
                dose(treatmentId, "2026-01-06T08:00:00") // alta zi — nu trebuie sa apara
            )
        )

        val items = doseDao.observeForDate("2026-01-05").first()

        assertEquals(1, items.size)
        assertEquals("Paracetamol", items.first().medicationName)
    }

    @Test
    fun updateStatus_changesStatusAndTakenAt() = runTest {
        val treatmentId = insertTreatment()
        doseDao.insertAll(listOf(dose(treatmentId, "2026-01-05T08:00:00")))
        val doseId = doseDao.observeForDate("2026-01-05").first().first().dose.id

        doseDao.updateStatus(doseId, "TAKEN", "2026-01-05T08:05:00")

        val updated = doseDao.getItemById(doseId)
        assertEquals("TAKEN", updated?.dose?.status)
        assertEquals("2026-01-05T08:05:00", updated?.dose?.takenAt)
    }

    @Test
    fun deletingParentTreatment_cascadesDeleteOfDoseLogs() = runTest {
        val treatmentId = insertTreatment()
        doseDao.insertAll(
            listOf(dose(treatmentId, "2026-01-05T08:00:00"), dose(treatmentId, "2026-01-05T20:00:00"))
        )
        assertEquals(2, doseDao.getBetween("2026-01-01T00:00:00", "2026-12-31T23:59:59").size)

        treatmentDao.deleteById(treatmentId)

        // ForeignKey.CASCADE pe DoseLogEntity.treatmentId -> dozele orfane trebuie sa dispara.
        assertTrue(doseDao.getBetween("2026-01-01T00:00:00", "2026-12-31T23:59:59").isEmpty())
    }

    @Test
    fun getFuturePendingIds_thenDeleteFuturePending_removesOnlyFuturePendingDoses() = runTest {
        val treatmentId = insertTreatment()
        doseDao.insertAll(
            listOf(
                dose(treatmentId, "2026-01-01T08:00:00", status = "TAKEN"),      // trecut, alt status -> nu se atinge
                dose(treatmentId, "2026-01-10T08:00:00", status = "PENDING"),    // viitor, pending -> tinta
                dose(treatmentId, "2026-01-11T08:00:00", status = "PENDING")     // viitor, pending -> tinta
            )
        )

        val futureIds = doseDao.getFuturePendingIds(treatmentId, "2026-01-05T00:00:00")
        assertEquals(2, futureIds.size)

        doseDao.deleteFuturePending(treatmentId, "2026-01-05T00:00:00")

        val remaining = doseDao.getBetween("2026-01-01T00:00:00", "2026-12-31T23:59:59")
        assertEquals(1, remaining.size)
        assertEquals("TAKEN", remaining.first().status)
    }
}
