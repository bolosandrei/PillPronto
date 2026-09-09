package com.pillpronto.data.notification

/**
 * Logica pura de deduplicare pentru notificarea de doza ratata (Faza 1.5d) — separata de
 * `CaregiverAlertWorker` ca sa fie testabila direct in JVM (Worker-ul atinge Context/WorkManager,
 * netestabil in unit test — acelasi motiv ca `ReminderSync`/`PatientProfileIdProvider` din 1.5c).
 *
 * Deduplicare pe multime de id-uri (nu pe timestamp): o doza MISSED e un status terminal — o
 * data notificata, ramane in setul "deja notificat" pentru totdeauna (setul creste doar cat
 * exista doze noi ratate, marime rezonabila pentru orizontul unui studiu pilot).
 */
object MissedDoseChecker {
    fun findNew(currentlyMissedRemoteIds: Set<String>, alreadyNotifiedRemoteIds: Set<String>): Set<String> =
        currentlyMissedRemoteIds - alreadyNotifiedRemoteIds
}
