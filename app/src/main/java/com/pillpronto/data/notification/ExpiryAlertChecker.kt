package com.pillpronto.data.notification

import com.pillpronto.domain.model.NEAR_EXPIRY_DAYS_THRESHOLD
import com.pillpronto.domain.model.Treatment
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ExpiryStage { NEAR_EXPIRY, EXPIRED }

data class ExpiryAlert(
    val treatmentId: Long,
    val medicationName: String,
    val expiryDate: LocalDate,
    val stage: ExpiryStage,
    val dedupKey: String
)

/**
 * Logica pura de verificare/deduplicare pt. alertele de expirare (separata de `ExpiryAlertWorker`
 * ca sa fie testabila direct in JVM — acelasi motiv ca `MissedDoseChecker`).
 *
 * Deduplicare pe cheie COMPUSA (`treatmentId:expiryDate:stage`), nu doar pe `treatmentId`: o
 * rescanare (cutie noua, alta data de expirare) capata automat propriile alerte, fara niciun
 * mecanism explicit de reset — cheile vechi raman in set, dar nu se mai potrivesc niciodata
 * (expiryDate-ul vechi nu mai apare pe niciun tratament activ).
 */
object ExpiryAlertChecker {
    fun computeAlerts(treatments: List<Treatment>, today: LocalDate, alreadyNotified: Set<String>): List<ExpiryAlert> =
        treatments.mapNotNull { treatment ->
            val expiryDate = treatment.expiryDate ?: return@mapNotNull null
            val daysUntilExpiry = ChronoUnit.DAYS.between(today, expiryDate)
            val stage = when {
                daysUntilExpiry < 0 -> ExpiryStage.EXPIRED
                daysUntilExpiry <= NEAR_EXPIRY_DAYS_THRESHOLD -> ExpiryStage.NEAR_EXPIRY
                else -> return@mapNotNull null
            }
            val dedupKey = "${treatment.id}:$expiryDate:$stage"
            if (dedupKey in alreadyNotified) return@mapNotNull null
            ExpiryAlert(treatment.id, treatment.medicationName, expiryDate, stage, dedupKey)
        }
}
