package com.pillpronto.data.sync

import com.pillpronto.data.local.entity.DoseLogEntity
import com.pillpronto.data.local.entity.PendingRemoteDeleteEntity
import com.pillpronto.data.local.entity.TreatmentEntity
import com.pillpronto.data.mapper.toIsoInstant
import com.pillpronto.data.remote.dto.TreatmentDto
import com.pillpronto.domain.model.AccountRole
import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.model.Profile
import com.pillpronto.domain.usecase.GenerateDosesUseCase
import com.pillpronto.util.FakeAuthRepository
import com.pillpronto.util.FakeDoseDao
import com.pillpronto.util.FakeDoseRepository
import com.pillpronto.util.FakePatientProfileIdProvider
import com.pillpronto.util.FakePendingRemoteDeleteDao
import com.pillpronto.util.FakeProfileRepository
import com.pillpronto.util.FakeReminderSync
import com.pillpronto.util.FakeSyncRemoteDataSource
import com.pillpronto.util.FakeTreatmentDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

private const val PATIENT_ID = "test-patient"

/** Teste pentru orchestrarea de sincronizare (Faza 1.5c) — vezi docs/user-management-plan.md
 * sectiunea 4 si CLAUDE.md sectiunea 7 pentru context. */
class SyncManagerTest {

    private val treatmentDao = FakeTreatmentDao()
    private val doseDao = FakeDoseDao()
    private val pendingRemoteDeleteDao = FakePendingRemoteDeleteDao()
    private val patientProfileIdProvider = FakePatientProfileIdProvider(PATIENT_ID)
    private val authRepository = FakeAuthRepository()
    private val profileRepository = FakeProfileRepository()
    private val remoteDataSource = FakeSyncRemoteDataSource()
    private val doseRepository = FakeDoseRepository()
    private val reminderSync = FakeReminderSync()

    private val syncManager = SyncManager(
        treatmentDao = treatmentDao,
        doseDao = doseDao,
        pendingRemoteDeleteDao = pendingRemoteDeleteDao,
        localPatientProfileProvider = patientProfileIdProvider,
        authRepository = authRepository,
        profileRepository = profileRepository,
        remoteDataSource = remoteDataSource,
        doseRepository = doseRepository,
        generateDoses = GenerateDosesUseCase(doseRepository),
        reminderSync = reminderSync
    )

    private fun authenticateAsPatient(userId: String = "user-1") {
        authRepository.emit(AuthSessionState.Authenticated(userId))
        profileRepository.profiles[userId] = Profile(userId, AccountRole.PATIENT, "Pacient")
    }

    private fun treatment(
        remoteId: String,
        name: String = "Paracetamol",
        updatedAt: Long = 100L,
        dirty: Boolean = true
    ) = TreatmentEntity(
        patientProfileId = PATIENT_ID,
        medicationName = name,
        dosage = "500 mg",
        timesCsv = "08:00",
        startDate = LocalDate.now().toString(),
        endDate = null,
        active = true,
        remoteId = remoteId,
        updatedAt = updatedAt,
        dirty = dirty
    )

    @Test
    fun `sync nu face nimic daca userul nu e autentificat`() = runTest {
        treatmentDao.upsert(treatment("r1"))

        syncManager.sync()

        assertTrue(remoteDataSource.treatments.isEmpty())
        assertEquals(0, reminderSync.callCount)
    }

    @Test
    fun `sync nu face nimic daca rolul nu e Pacient`() = runTest {
        authRepository.emit(AuthSessionState.Authenticated("user-1"))
        profileRepository.profiles["user-1"] = Profile("user-1", AccountRole.CAREGIVER, "Aparținător")
        treatmentDao.upsert(treatment("r1"))

        syncManager.sync()

        assertTrue(remoteDataSource.treatments.isEmpty())
    }

    @Test
    fun `push trimite doar tratamentele dirty si le curata dupa succes`() = runTest {
        authenticateAsPatient()
        val cleanId = treatmentDao.upsert(treatment("clean", updatedAt = 50, dirty = false))
        val dirtyId = treatmentDao.upsert(treatment("dirty", updatedAt = 100, dirty = true))

        syncManager.sync()

        assertFalse(remoteDataSource.treatments.containsKey("clean"))
        assertTrue(remoteDataSource.treatments.containsKey("dirty"))
        assertFalse(treatmentDao.getById(dirtyId)!!.dirty)
        assertEquals(cleanId, treatmentDao.getById(cleanId)!!.id) // neatins
    }

    @Test
    fun `push trimite doar dozele cu status final, niciodata PENDING`() = runTest {
        authenticateAsPatient()
        doseDao.treatmentRemoteIdByTreatmentId = mapOf(1L to "tr-1")
        doseDao.seed(dose(treatmentId = 1L, status = "TAKEN", remoteId = "taken-dose"))
        doseDao.seed(dose(treatmentId = 1L, status = "PENDING", dirty = true, remoteId = "pending-dose"))

        syncManager.sync()

        assertTrue(remoteDataSource.doseLogs.containsKey("taken-dose"))
        assertFalse(remoteDataSource.doseLogs.containsKey("pending-dose"))
    }

    @Test
    fun `push esuat lasa randul dirty si nu blocheaza restul ciclului`() = runTest {
        authenticateAsPatient()
        remoteDataSource.pushTreatmentError = RuntimeException("reteaua a picat")
        val id = treatmentDao.upsert(treatment("r1", updatedAt = 100, dirty = true))

        syncManager.sync()

        assertTrue(remoteDataSource.treatments.isEmpty())
        assertTrue(treatmentDao.getById(id)!!.dirty)
    }

    @Test
    fun `pull nu suprascrie o editare locala nepushuita`() = runTest {
        authenticateAsPatient()
        remoteDataSource.pushTreatmentError = RuntimeException("reteaua a picat") // pastreaza local dirty
        treatmentDao.upsert(treatment("r1", name = "Local nou", updatedAt = 100, dirty = true))
        remoteDataSource.treatments["r1"] = remoteDto("r1", name = "Remote vechi", updatedAtMillis = 999_999)

        syncManager.sync()

        val stored = treatmentDao.getByRemoteId("r1")
        assertEquals("Local nou", stored?.medicationName)
    }

    @Test
    fun `pull aplica randul remote doar daca e mai nou (last-write-wins)`() = runTest {
        authenticateAsPatient()
        treatmentDao.upsert(treatment("r1", name = "Vechi", updatedAt = 1_000, dirty = false))
        remoteDataSource.treatments["r1"] = remoteDto("r1", name = "Mai vechi remote", updatedAtMillis = 500)

        syncManager.sync()

        assertEquals("Vechi", treatmentDao.getByRemoteId("r1")?.medicationName) // remote mai vechi, ignorat
    }

    @Test
    fun `pull suprascrie local cu randul remote mai nou`() = runTest {
        authenticateAsPatient()
        treatmentDao.upsert(treatment("r1", name = "Vechi", updatedAt = 1_000, dirty = false))
        remoteDataSource.treatments["r1"] = remoteDto("r1", name = "Nou remote", updatedAtMillis = 2_000)

        syncManager.sync()

        assertEquals("Nou remote", treatmentDao.getByRemoteId("r1")?.medicationName)
    }

    @Test
    fun `pull unui tratament nou insereaza local si regenereaza dozele PENDING`() = runTest {
        authenticateAsPatient()
        remoteDataSource.treatments["nou"] = remoteDto("nou", name = "Tratament nou", updatedAtMillis = 1_000)

        syncManager.sync()

        val local = treatmentDao.getByRemoteId("nou")
        assertTrue(local != null && !local.dirty)
        val now = LocalDateTime.now()
        assertTrue(doseRepository.getLogsBetween(now, now).isNotEmpty()) // GenerateDosesUseCase a rulat
        assertEquals(1, reminderSync.callCount)
    }

    @Test
    fun `stergerea remote reuseste si consuma tombstone-ul`() = runTest {
        authenticateAsPatient()
        remoteDataSource.treatments["de-sters"] = remoteDto("de-sters", updatedAtMillis = 1)
        pendingRemoteDeleteDao.insert(PendingRemoteDeleteEntity("de-sters"))

        syncManager.sync()

        assertTrue(remoteDataSource.deletedTreatmentIds.contains("de-sters"))
        assertTrue(pendingRemoteDeleteDao.getAll().isEmpty())
    }

    @Test
    fun `stergerea remote esuata pastreaza tombstone-ul pentru urmatorul ciclu`() = runTest {
        authenticateAsPatient()
        remoteDataSource.deleteTreatmentError = RuntimeException("reteaua a picat")
        pendingRemoteDeleteDao.insert(PendingRemoteDeleteEntity("de-sters"))

        syncManager.sync()

        assertFalse(pendingRemoteDeleteDao.getAll().isEmpty())
    }

    private fun dose(
        treatmentId: Long,
        status: String,
        remoteId: String,
        dirty: Boolean = true,
        updatedAt: Long = 100L
    ) = DoseLogEntity(
        patientProfileId = PATIENT_ID,
        treatmentId = treatmentId,
        scheduledAt = "2026-01-05T08:00:00",
        status = status,
        takenAt = if (status == "TAKEN") "2026-01-05T08:05:00" else null,
        remoteId = remoteId,
        updatedAt = updatedAt,
        dirty = dirty
    )

    private fun remoteDto(id: String, name: String = "Tratament", updatedAtMillis: Long) = TreatmentDto(
        id = id,
        patientProfileId = PATIENT_ID,
        medicationName = name,
        dosage = "1 doză",
        timesCsv = "23:59",
        startDate = LocalDate.now().toString(),
        endDate = null,
        active = true,
        asNeeded = false,
        updatedAt = updatedAtMillis.toIsoInstant()
    )
}
