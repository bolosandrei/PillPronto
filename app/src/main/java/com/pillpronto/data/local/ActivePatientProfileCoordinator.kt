package com.pillpronto.data.local

import android.util.Log
import com.pillpronto.core.di.ApplicationScope
import com.pillpronto.data.local.dao.LocalAccountProfileDao
import com.pillpronto.data.local.dao.TreatmentDao
import com.pillpronto.data.local.entity.LocalAccountProfileEntity
import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.repository.AuthRepository
import com.pillpronto.domain.repository.GuestProfileIdProvider
import com.pillpronto.domain.repository.LocalDataMigrationCoordinator
import com.pillpronto.domain.repository.LocalDataMigrationPrompt
import com.pillpronto.domain.repository.PatientProfileIdProvider
import com.pillpronto.domain.repository.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rezolva ce profil local (`patientProfileId`) e activ pe acest device, in functie de contul
 * autentificat curent — fix pt. bug-ul de izolare a datelor locale (CLAUDE.md): inainte, un
 * singur UUID exista per instalare, impartit de toate conturile, iar niciun query Room nu-l
 * folosea ca filtru la citire — un cont nou vedea local tratamentele contului anterior.
 *
 * Decizie confirmata cu userul (2026-10-12): FIECARE cont, inclusiv primul, primeste implicit un
 * profil local NOU si gol — fara adoptie automata a datelor "guest" (bucket-ul
 * `LocalPatientProfileProvider.guestProfileId`, folosit cand nu exista sesiune autentificata).
 * In loc de adoptie automata: prompt explicit, o singura data, cand un cont NOU (fara mapare
 * existenta, fara identitate remote existenta) gaseste bucket-ul guest cu date neclaimate.
 *
 * Rezolvare la fiecare schimbare de sesiune (vezi `resolveForSession`):
 * 1. Mapare locala existenta pt. acest userId? -> se reutilizeaza (cont revine pe device cunoscut).
 * 2. Nu exista mapare, dar contul are deja un rand `patient_profiles` pe server (alt device/
 *    sesiune anterioara)? -> se adopta acel id remote, FARA prompt (nu amestecam date guest
 *    necunoscute cu identitatea deja stabilita a unui cont).
 * 3. Nu exista nici mapare, nici rand remote, dar bucket-ul guest are date locale neclaimate
 *    (niciun alt cont nu-l tinteste deja) -> prompt explicit in UI (AccountScreen), vezi
 *    `pendingPrompt`/`resolveMigrationChoice`.
 * 4. Niciuna din cele de mai sus -> profil local nou, gol, generat pe loc.
 */
@Singleton
class ActivePatientProfileCoordinator @Inject constructor(
    private val legacyProvider: GuestProfileIdProvider,
    private val localAccountProfileDao: LocalAccountProfileDao,
    private val treatmentDao: TreatmentDao,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    @ApplicationScope private val appScope: CoroutineScope
) : PatientProfileIdProvider, LocalDataMigrationCoordinator {

    private val _patientProfileId = MutableStateFlow(legacyProvider.guestProfileId)
    override val patientProfileId: StateFlow<String> = _patientProfileId.asStateFlow()

    private val _pendingPrompt = MutableStateFlow<LocalDataMigrationPrompt?>(null)
    override val pendingPrompt: StateFlow<LocalDataMigrationPrompt?> = _pendingPrompt.asStateFlow()

    // Numarul de rezolvari finalizate — currentProfileId() asteapta ca acesta sa avanseze dupa
    // ultima schimbare de sesiune cunoscuta, ca sa nu intoarca o valoare tranzitorie (vezi doc pe
    // PatientProfileIdProvider.currentProfileId). Pornim de la 1 (rezolvarea initiala, sincrona,
    // pt. starea "Unauthenticated"/"Loading" de mai sus, e deja valida din constructie).
    private val _resolvedGeneration = MutableStateFlow(1)
    private var latestSessionUserId: String? = null

    /** Trebuie apelat o singura data, din `PillProntoApp.onCreate()` — porneste colectarea
     * sesiunii de autentificare pe toata durata de viata a aplicatiei (NU doar cat un ecran e
     * vizibil, ca la ViewModel-urile existente). */
    fun start() {
        authRepository.sessionStatus
            .onEach { session -> resolveForSession(session) }
            .launchIn(appScope)
    }

    override suspend fun currentProfileId(): String {
        val targetUserId = latestSessionUserId
        if (targetUserId == null) return _patientProfileId.value
        // Asteapta prima rezolvare GENERATA DUPA ce userId-ul tinta a fost inregistrat — evita o
        // cursa reala: un apel facut chiar in clipa unui login proaspat poate prinde rezolvarea
        // inca in curs (generation neavansat). Fara timeout explicit: resolveForSession nu face
        // operatii care ar putea bloca la nesfarsit (query-uri locale + cel mult un SELECT remote).
        _resolvedGeneration.first { it > 0 && latestSessionUserId == targetUserId && hasResolvedCurrent }
        return _patientProfileId.value
    }

    @Volatile
    private var hasResolvedCurrent = true

    override suspend fun resolveMigrationChoice(userId: String, keepGuestData: Boolean) {
        val chosenId = if (keepGuestData) legacyProvider.guestProfileId else UUID.randomUUID().toString()
        localAccountProfileDao.insert(LocalAccountProfileEntity(userId, chosenId, System.currentTimeMillis()))
        _patientProfileId.value = chosenId
        _pendingPrompt.value = null
        markResolved()
    }

    private suspend fun resolveForSession(session: AuthSessionState) {
        when (session) {
            is AuthSessionState.Authenticated -> resolveForAccount(session.userId)
            else -> {
                latestSessionUserId = null
                _pendingPrompt.value = null
                _patientProfileId.value = legacyProvider.guestProfileId
                markResolved()
            }
        }
    }

    private suspend fun resolveForAccount(userId: String) {
        latestSessionUserId = userId
        hasResolvedCurrent = false
        val resolvedConcretely = runCatching { resolveForAccountInternal(userId) }
            .onFailure { Log.e(TAG, "Nu am putut rezolva profilul local activ pt. $userId", it) }
            .getOrDefault(true) // eroare -> nu lasam currentProfileId() blocat la nesfarsit
        // Doar o rezolvare CONCRETA (mapare gasita/adoptata/noua) avanseaza starea "rezolvat" —
        // un prompt in asteptare NU e o rezolvare, currentProfileId() trebuie sa continue sa
        // astepte raspunsul userului (vezi resolveMigrationChoice), nu sa citeasca valoarea veche
        // tranzitorie (inca bucket-ul guest, netintit de fapt catre acest cont).
        if (resolvedConcretely) markResolved()
    }

    /** @return false DOAR cand rezolvarea s-a oprit la un prompt in asteptare (nu e inca
     * concreta) — orice alta ramura (mapare existenta/adoptie remote/profil nou) e concreta. */
    private suspend fun resolveForAccountInternal(userId: String): Boolean {
        localAccountProfileDao.getByUserId(userId)?.let {
            _patientProfileId.value = it.patientProfileId
            return true
        }

        val remoteExistingId = profileRepository.findExistingPatientProfileId(userId)
        if (remoteExistingId != null) {
            localAccountProfileDao.insert(LocalAccountProfileEntity(userId, remoteExistingId, System.currentTimeMillis()))
            _patientProfileId.value = remoteExistingId
            return true
        }

        val guestId = legacyProvider.guestProfileId
        val guestHasData = treatmentDao.hasAnyForProfile(guestId)
        val guestAlreadyClaimed = localAccountProfileDao.getByPatientProfileId(guestId) != null
        if (guestHasData && !guestAlreadyClaimed) {
            _pendingPrompt.value = LocalDataMigrationPrompt(userId, guestId)
            return false // asteapta decizia userului — vezi resolveMigrationChoice
        }

        val newId = UUID.randomUUID().toString()
        localAccountProfileDao.insert(LocalAccountProfileEntity(userId, newId, System.currentTimeMillis()))
        _patientProfileId.value = newId
        return true
    }

    private fun markResolved() {
        hasResolvedCurrent = true
        _resolvedGeneration.value += 1
    }

    private companion object {
        const val TAG = "ActivePatientProfile"
    }
}
