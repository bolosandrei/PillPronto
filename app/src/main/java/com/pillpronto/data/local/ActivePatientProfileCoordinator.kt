package com.pillpronto.data.local

import android.util.Log
import com.pillpronto.core.di.ApplicationScope
import com.pillpronto.data.local.dao.DoseDao
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
 * Decizie confirmata cu userul (2026-10-12, revizuita dupa testare live): FIECARE cont fara
 * mapare locala existenta pe acest device primeste explicit alegerea — niciodata adoptie
 * silentioasa a datelor "guest" (bucket-ul `LocalPatientProfileProvider.guestProfileId`, folosit
 * cand nu exista sesiune autentificata), INDIFERENT daca contul are deja o identitate remote
 * stabilita (`patient_profiles`, de pe alt device/sesiune anterioara) sau e genuin nou.
 *
 * "Pastreaza datele guest" MUTA efectiv randurile locale (`TreatmentDao`/`DoseDao.reassignProfile`)
 * din bucket-ul guest in identitatea tinta a contului — NU face bucket-ul guest sa "devina" acel
 * cont (ar intra in conflict cu `unique(user_id)` din `patient_profiles` daca contul are deja o
 * alta identitate remote stabilita). Bucket-ul guest ramane mereu strict al utilizarii fara cont.
 *
 * Rezolvare la fiecare schimbare de sesiune (vezi `resolveForSession`):
 * 1. Mapare locala existenta pt. acest userId? -> se reutilizeaza (cont revine pe device cunoscut,
 *    deja a trecut prin pasii de mai jos intr-o sesiune anterioara).
 * 2. Nu exista mapare — se determina id-ul TINTA: randul `patient_profiles` existent al contului
 *    (alt device/sesiune anterioara) sau, daca nu exista, un UUID nou.
 * 3. Bucket-ul guest are date locale? -> prompt explicit in UI (AccountScreen), vezi
 *    `pendingPrompt`/`resolveMigrationChoice` — userul alege intre "pastreaza" (muta randurile
 *    guest in id-ul tinta) si "incepe cu profil gol" (id-ul tinta, bucket-ul guest neatins).
 * 4. Bucket-ul guest e gol -> id-ul tinta se aplica direct, fara prompt (nimic de ales).
 */
@Singleton
class ActivePatientProfileCoordinator @Inject constructor(
    private val legacyProvider: GuestProfileIdProvider,
    private val localAccountProfileDao: LocalAccountProfileDao,
    private val treatmentDao: TreatmentDao,
    private val doseDao: DoseDao,
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
        val prompt = _pendingPrompt.value ?: return
        if (keepGuestData) {
            val guestId = prompt.guestProfileId
            val targetId = prompt.targetProfileId
            treatmentDao.reassignProfile(fromPatientProfileId = guestId, toPatientProfileId = targetId)
            doseDao.reassignProfile(fromPatientProfileId = guestId, toPatientProfileId = targetId)
        }
        localAccountProfileDao.insert(LocalAccountProfileEntity(userId, prompt.targetProfileId, System.currentTimeMillis()))
        _patientProfileId.value = prompt.targetProfileId
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
     * concreta) — orice alta ramura (mapare existenta/id tinta aplicat direct) e concreta. */
    private suspend fun resolveForAccountInternal(userId: String): Boolean {
        localAccountProfileDao.getByUserId(userId)?.let {
            _patientProfileId.value = it.patientProfileId
            return true
        }

        val remoteExistingId = profileRepository.findExistingPatientProfileId(userId)
        val targetId = remoteExistingId ?: UUID.randomUUID().toString()

        val guestId = legacyProvider.guestProfileId
        if (treatmentDao.hasAnyForProfile(guestId)) {
            _pendingPrompt.value = LocalDataMigrationPrompt(
                userId = userId,
                guestProfileId = guestId,
                targetProfileId = targetId,
                accountHasExistingRemoteIdentity = remoteExistingId != null
            )
            return false // asteapta decizia userului — vezi resolveMigrationChoice
        }

        localAccountProfileDao.insert(LocalAccountProfileEntity(userId, targetId, System.currentTimeMillis()))
        _patientProfileId.value = targetId
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
