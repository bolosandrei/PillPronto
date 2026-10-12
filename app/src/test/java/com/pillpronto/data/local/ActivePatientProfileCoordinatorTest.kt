package com.pillpronto.data.local

import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.repository.LocalDataMigrationPrompt
import com.pillpronto.util.FakeAuthRepository
import com.pillpronto.util.FakeGuestProfileIdProvider
import com.pillpronto.util.FakeLocalAccountProfileDao
import com.pillpronto.util.FakeProfileRepository
import com.pillpronto.util.FakeTreatmentDao
import com.pillpronto.data.local.entity.TreatmentEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val GUEST_ID = "guest-id"

/** Teste pentru fix-ul de izolare a datelor locale — vezi CLAUDE.md pt. context complet. Cele 4
 * ramuri de rezolvare: mapare existenta / adoptie id remote existent / prompt (guest cu date
 * neclaimate) / profil nou gol. */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivePatientProfileCoordinatorTest {

    private val legacyProvider = FakeGuestProfileIdProvider(GUEST_ID)
    private val localAccountProfileDao = FakeLocalAccountProfileDao()
    private val treatmentDao = FakeTreatmentDao()
    private val authRepository = FakeAuthRepository()
    private val profileRepository = FakeProfileRepository()

    private fun createCoordinator(appScope: CoroutineScope) = ActivePatientProfileCoordinator(
        legacyProvider = legacyProvider,
        localAccountProfileDao = localAccountProfileDao,
        treatmentDao = treatmentDao,
        authRepository = authRepository,
        profileRepository = profileRepository,
        appScope = appScope
    )

    @Test
    fun `fara sesiune, profilul activ e bucket-ul guest`() = runTest {
        val coordinator = createCoordinator(CoroutineScope(UnconfinedTestDispatcher()))
        coordinator.start()

        assertEquals(GUEST_ID, coordinator.patientProfileId.value)
    }

    @Test
    fun `cont nou, fara date remote, fara date guest, primeste profil local nou gol`() = runTest {
        val coordinator = createCoordinator(CoroutineScope(UnconfinedTestDispatcher()))
        coordinator.start()

        authRepository.emit(AuthSessionState.Authenticated("user-1"))

        val resolved = coordinator.patientProfileId.value
        assertNotEquals(GUEST_ID, resolved)
        assertNull(coordinator.pendingPrompt.value)
        assertEquals(resolved, localAccountProfileDao.rows["user-1"]?.patientProfileId)
    }

    @Test
    fun `cont nou cu identitate remote existenta adopta id-ul remote, fara prompt`() = runTest {
        profileRepository.existingRemotePatientProfileIds["user-1"] = "remote-id-de-pe-alt-device"
        val coordinator = createCoordinator(CoroutineScope(UnconfinedTestDispatcher()))
        coordinator.start()

        authRepository.emit(AuthSessionState.Authenticated("user-1"))

        assertEquals("remote-id-de-pe-alt-device", coordinator.patientProfileId.value)
        assertNull(coordinator.pendingPrompt.value)
    }

    @Test
    fun `cont nou, bucket guest are date neclaimate, afiseaza prompt fara rezolvare automata`() = runTest {
        treatmentDao.upsert(treatment(patientProfileId = GUEST_ID))
        val coordinator = createCoordinator(CoroutineScope(UnconfinedTestDispatcher()))
        coordinator.start()

        authRepository.emit(AuthSessionState.Authenticated("user-1"))

        assertEquals(LocalDataMigrationPrompt("user-1", GUEST_ID), coordinator.pendingPrompt.value)
        // Profilul activ NU a fost inca schimbat — asteapta decizia userului.
        assertEquals(GUEST_ID, coordinator.patientProfileId.value)
    }

    @Test
    fun `cont nou, bucket guest deja claimat de alt cont, NU mai ofera promptul, primeste profil nou gol`() = runTest {
        treatmentDao.upsert(treatment(patientProfileId = GUEST_ID))
        localAccountProfileDao.insert(
            com.pillpronto.data.local.entity.LocalAccountProfileEntity("user-A", GUEST_ID, 0L)
        )
        val coordinator = createCoordinator(CoroutineScope(UnconfinedTestDispatcher()))
        coordinator.start()

        authRepository.emit(AuthSessionState.Authenticated("user-2"))

        assertNull(coordinator.pendingPrompt.value)
        assertNotEquals(GUEST_ID, coordinator.patientProfileId.value)
    }

    @Test
    fun `resolveMigrationChoice keepGuestData=true adopta bucket-ul guest`() = runTest {
        treatmentDao.upsert(treatment(patientProfileId = GUEST_ID))
        val coordinator = createCoordinator(CoroutineScope(UnconfinedTestDispatcher()))
        coordinator.start()
        authRepository.emit(AuthSessionState.Authenticated("user-1"))

        coordinator.resolveMigrationChoice("user-1", keepGuestData = true)

        assertEquals(GUEST_ID, coordinator.patientProfileId.value)
        assertNull(coordinator.pendingPrompt.value)
        assertEquals(GUEST_ID, localAccountProfileDao.rows["user-1"]?.patientProfileId)
    }

    @Test
    fun `resolveMigrationChoice keepGuestData=false genereaza un profil nou, separat de guest`() = runTest {
        treatmentDao.upsert(treatment(patientProfileId = GUEST_ID))
        val coordinator = createCoordinator(CoroutineScope(UnconfinedTestDispatcher()))
        coordinator.start()
        authRepository.emit(AuthSessionState.Authenticated("user-1"))

        coordinator.resolveMigrationChoice("user-1", keepGuestData = false)

        assertNotEquals(GUEST_ID, coordinator.patientProfileId.value)
        assertNull(coordinator.pendingPrompt.value)
    }

    @Test
    fun `cont care revine pe acelasi device reutilizeaza maparea existenta, nu una noua`() = runTest {
        localAccountProfileDao.insert(
            com.pillpronto.data.local.entity.LocalAccountProfileEntity("user-1", "profil-cunoscut", 0L)
        )
        val coordinator = createCoordinator(CoroutineScope(UnconfinedTestDispatcher()))
        coordinator.start()

        authRepository.emit(AuthSessionState.Authenticated("user-1"))

        assertEquals("profil-cunoscut", coordinator.patientProfileId.value)
    }

    @Test
    fun `doi conturi diferite pe acelasi device primesc profiluri locale diferite`() = runTest {
        val coordinator = createCoordinator(CoroutineScope(UnconfinedTestDispatcher()))
        coordinator.start()

        authRepository.emit(AuthSessionState.Authenticated("user-A"))
        val idA = coordinator.patientProfileId.value

        authRepository.emit(AuthSessionState.Authenticated("user-B"))
        val idB = coordinator.patientProfileId.value

        assertNotEquals(idA, idB)
    }

    @Test
    fun `currentProfileId intoarce valoarea rezolvata, nu una tranzitorie`() = runTest {
        val coordinator = createCoordinator(CoroutineScope(UnconfinedTestDispatcher()))
        coordinator.start()
        authRepository.emit(AuthSessionState.Authenticated("user-1"))

        val resolved = coordinator.currentProfileId()

        assertEquals(coordinator.patientProfileId.value, resolved)
    }

    private fun treatment(patientProfileId: String) = TreatmentEntity(
        patientProfileId = patientProfileId,
        medicationName = "Paracetamol",
        dosage = "500 mg",
        timesCsv = "08:00",
        startDate = "2026-01-01",
        endDate = null,
        active = true,
        remoteId = "remote-1",
        updatedAt = 0L
    )
}
