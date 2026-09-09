package com.pillpronto.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pillpronto.data.notification.CaregiverAlertNotifier
import com.pillpronto.data.notification.MissedDoseChecker
import com.pillpronto.data.notification.NotifiedMissedDosesStore
import com.pillpronto.domain.model.AccountRole
import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.repository.AuthRepository
import com.pillpronto.domain.repository.ProfileRepository
import com.pillpronto.domain.usecase.GetLinkedPatientDataUseCase
import com.pillpronto.domain.usecase.GetMyPatientsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Verificare periodica a dozelor ratate ale pacientilor legati — no-op daca userul curent nu e
 * autentificat ca Apartinator (Faza 1.5d). Vezi `MissedDoseChecker` pentru logica de deduplicare
 * (testabila separat).
 */
@HiltWorker
class CaregiverAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val getMyPatients: GetMyPatientsUseCase,
    private val getLinkedPatientData: GetLinkedPatientDataUseCase,
    private val notifiedDosesStore: NotifiedMissedDosesStore,
    private val notifier: CaregiverAlertNotifier
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val userId = currentCaregiverUserId()
        if (userId != null) checkPatients(userId)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    private suspend fun currentCaregiverUserId(): String? {
        val session = withTimeoutOrNull(SESSION_SNAPSHOT_TIMEOUT_MS) {
            authRepository.sessionStatus.first { it !is AuthSessionState.Loading }
        } ?: return null
        val userId = (session as? AuthSessionState.Authenticated)?.userId ?: return null
        val profile = profileRepository.getProfile(userId) ?: return null
        return userId.takeIf { profile.role == AccountRole.CAREGIVER }
    }

    private suspend fun checkPatients(userId: String) {
        val patients = getMyPatients(userId)
        var alreadyNotified = notifiedDosesStore.getNotifiedIds()

        patients.forEach { patient ->
            val data = getLinkedPatientData(patient.patientProfileId)
            val currentlyMissed = data.doseLogs
                .filter { it.log.status == DoseStatus.MISSED }
                .map { it.remoteId }
                .toSet()
            val newOnes = MissedDoseChecker.findNew(currentlyMissed, alreadyNotified)
            if (newOnes.isNotEmpty()) notifier.showMissedDoseAlert(patient.displayName)
            alreadyNotified = alreadyNotified + currentlyMissed
        }

        notifiedDosesStore.setNotifiedIds(alreadyNotified)
    }

    companion object {
        const val UNIQUE_NAME = "caregiver_alerts"
        private const val SESSION_SNAPSHOT_TIMEOUT_MS = 5_000L
    }
}
