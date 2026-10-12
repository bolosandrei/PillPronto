package com.pillpronto.data.local

import com.pillpronto.data.local.entity.LocalAccountProfileEntity
import com.pillpronto.data.local.entity.TreatmentEntity
import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.util.FakeAuthRepository
import com.pillpronto.util.FakeDoseDao
import com.pillpronto.util.FakeGuestProfileIdProvider
import com.pillpronto.util.FakeLocalAccountProfileDao
import com.pillpronto.util.FakeProfileRepository
import com.pillpronto.util.FakeTreatmentDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val GUEST_ID = "guest-id"

/** Teste pentru fix-ul de izolare a datelor locale — vezi CLAUDE.md pt. context complet. Promptul
 * de migrare apare pt. ORICE cont fara mapare locala existenta, de cate ori bucket-ul guest are
 * date — indiferent daca contul are deja o identitate remote stabilita (decizie revizuita
 * 2026-10-12, dupa testare live: fara nicio adoptie silentioasa). */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivePatientProfileCoordinatorTest {

    private val legacyProvider = FakeGuestProfileIdProvider(GUEST_ID)
    private val localAccountProfileDao = FakeLocalAccountProfileDao()
    private val treatmentDao = FakeTreatmentDao()
    private val doseDao = FakeDoseDao()
    private val authRepository = FakeAuthRepository()
    private val profileRepository = FakeProfileRepository()

    private fun createCoordinator(appScope: CoroutineScope) = ActivePatientProfileCoordinator(
        legacyProvider = legacyProvider,
        localAccountProfileDao = localAccountProfileDao,
        treatmentDao = treatmentDao,
        doseDao = doseDao,
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
    fun `cont nou, fara date remote, fara date guest, primeste profil local nou gol direct`() = runTest {
        val coordinator = createCoordinator(CoroutineScope(UnconfinedTestDispatcher()))
        coordinator.start()

        authRepository.emit(AuthSessionState.Authenticated("user-1"))

        val resolved = coordinator.patientProfileId.value
        assertNotEquals(GUEST_ID, resolved)
        assertNull(coordinator.pendingPrompt.value)
        assertEquals(resolved, localAccountProfileDao.rows["user-1"]?.patientProfileId)
    }

    @Test
    fun `cont nou cu identitate remote existenta, fara date guest, adopta direct id-ul remote`() = runTest {
        profileRepository.existingRemotePatientProfileIds["user-1"] = "remote-id-de-pe-alt-device"
        val coordinator = createCoordinator(CoroutineScope(UnconfinedTestDispatcher()))
        coordinator.start()

        authRepository.emit(AuthSessionState.Authenticated("user-1"))

        assertEquals("remote-id-de-pe-alt-device", coordinator.patientProfileId.value)
        assertNull(coordinator.pendingPrompt.value)
    }

    @Test
    fun `cont nou, bucket guest are date, afiseaza prompt fara rezolvare automata`() = runTest {
        treatmentDao.upsert(treatment(patientProfileId = GUEST_ID))
        val coordinator = createCoordinator(CoroutineScope(UnconfinedTestDispatcher()))
        coordinator.start()

        authRepository.emit(AuthSessionState.Authenticated("user-1"))

        val prompt = coordinator.pendingPrompt.value
        assertEquals("user-1", prompt?.userId)
        assertEquals(GUEST_ID, prompt?.guestProfileId)
        assertFalse(prompt?.accountHasExistingRemoteIdentity ?: true)
        // Profilul activ NU a fost inca schimbat — asteapta decizia userului.
        assertEquals(GUEST_ID, coordinator.patientProfileId.value)
    }

    @Test
    fun `cont CU identitate remote existenta primeste TOT promptul cand bucket-ul guest are date`() = runTest {
        profileRepository.existingRemotePatientProfileIds["user-1"] = "remote-id-existent"
        treatmentDao.upsert(treatment(patientProfileId = GUEST_ID))
        val coordinator = createCoordinator(CoroutineScope(UnconfinedTestDispatcher()))
        coordinator.start()

        authRepository.emit(AuthSessionState.Authenticated("user-1"))

        val prompt = coordinator.pendingPrompt.value
        assertEquals("remote-id-existent", prompt?.targetProfileId)
        assertTrue(prompt?.accountHasExistingRemoteIdentity ?: false)
    }

    @Test
    fun `al doilea cont nu mai vede prompt dupa ce primul a mutat (merge-uit) datele guest`() = runTest {
        treatmentDao.upsert(treatment(patientProfileId = GUEST_ID))
        val coordinator = createCoordinator(CoroutineScope(UnconfinedTestDispatcher()))
        coordinator.start()
        authRepository.emit(AuthSessionState.Authenticated("user-1"))
        coordinator.resolveMigrationChoice("user-1", keepGuestData = true) // bucket-ul guest devine gol

        authRepository.emit(AuthSessionState.Authenticated("user-2"))

        assertNull(coordinator.pendingPrompt.value)
        assertNotEquals(GUEST_ID, coordinator.patientProfileId.value)
    }

    @Test
    fun `resolveMigrationChoice keepGuestData=true muta randurile din guest in id-ul tinta, NU in guest`() = runTest {
        treatmentDao.upsert(treatment(patientProfileId = GUEST_ID, name = "Al guestului"))
        val coordinator = createCoordinator(CoroutineScope(UnconfinedTestDispatcher()))
        coordinator.start()
        authRepository.emit(AuthSessionState.Authenticated("user-1"))

        coordinator.resolveMigrationChoice("user-1", keepGuestData = true)

        val resolvedId = coordinator.patientProfileId.value
        assertNotEquals(GUEST_ID, resolvedId) // id-ul tinta e NOU, nu bucket-ul guest insusi
        assertNull(coordinator.pendingPrompt.value)
        assertEquals(resolvedId, localAccountProfileDao.rows["user-1"]?.patientProfileId)
        assertFalse(treatmentDao.hasAnyForProfile(GUEST_ID)) // bucket-ul guest, golit prin merge
        assertTrue(treatmentDao.hasAnyForProfile(resolvedId))
    }

    @Test
    fun `resolveMigrationChoice keepGuestData=false genereaza un profil nou, bucket-ul guest neatins`() = runTest {
        treatmentDao.upsert(treatment(patientProfileId = GUEST_ID))
        val coordinator = createCoordinator(CoroutineScope(UnconfinedTestDispatcher()))
        coordinator.start()
        authRepository.emit(AuthSessionState.Authenticated("user-1"))

        coordinator.resolveMigrationChoice("user-1", keepGuestData = false)

        assertNotEquals(GUEST_ID, coordinator.patientProfileId.value)
        assertNull(coordinator.pendingPrompt.value)
        assertTrue(treatmentDao.hasAnyForProfile(GUEST_ID)) // neatins
    }

    @Test
    fun `cont care revine pe acelasi device reutilizeaza maparea existenta, nu una noua`() = runTest {
        localAccountProfileDao.insert(LocalAccountProfileEntity("user-1", "profil-cunoscut", 0L))
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

    private fun treatment(patientProfileId: String, name: String = "Paracetamol") = TreatmentEntity(
        patientProfileId = patientProfileId,
        medicationName = name,
        dosage = "500 mg",
        timesCsv = "08:00",
        startDate = "2026-01-01",
        endDate = null,
        active = true,
        remoteId = "remote-1",
        updatedAt = 0L
    )
}
