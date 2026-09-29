package com.pillpronto.domain.usecase

import com.pillpronto.data.reminder.ReminderSync
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.repository.DoseRepository
import javax.inject.Inject
import java.time.LocalDateTime

/** Confirma / omite o doza. Sursa principala de date pentru aderenta.
 *
 * Intoarce `false` (no-op tacut, NU exceptie) daca doza nu exista sau e in afara ferestrei de
 * actiune (vezi isDoseActionable) — o exceptie ar sari peste `cancelNotification` din
 * DoseActionReceiver (care ruleaza dupa acest apel, nu intr-un `finally`), lasand o notificare
 * agatata in bara de notificari. */
class LogDoseUseCase @Inject constructor(
    private val doseRepository: DoseRepository,
    private val reminderSync: ReminderSync
) {
    suspend operator fun invoke(doseId: Long, status: DoseStatus): Boolean {
        val item = doseRepository.getItemById(doseId) ?: return false
        if (!isDoseActionable(item.dose.scheduledAt)) return false
        val takenAt = if (status == DoseStatus.TAKEN) LocalDateTime.now() else null
        doseRepository.updateStatus(doseId, status, takenAt)
        // Statusul e final acum — daca alarma exacta inca n-a sunat (confirmare/omitere
        // anticipata, din ecranul "Azi"), n-are sens sa se mai declanseze la ora programata.
        reminderSync.cancelDose(doseId)
        return true
    }
}
