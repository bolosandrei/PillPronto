package com.pillpronto.domain.model

/**
 * Datele REMOTE (Supabase, niciodata in Room local) ale unui pacient legat, vazute de un
 * Apartinator (Faza 1.5d, read-only). `remoteId` insoteste fiecare `Treatment`/`DoseLog` pentru
 * ca modelele domain au `id: Long` (id-uri locale Room, fara sens pt. date care nu ajung
 * niciodata local) — folosit doar ca cheie stabila de UI (`LazyColumn`), pattern similar
 * `DoseItem` (compunere de citire, nu poluare a modelului domain de baza).
 */
data class LinkedTreatment(val remoteId: String, val treatment: Treatment)

/** `treatmentRemoteId` leaga doza de `LinkedTreatment.remoteId` (nu de `DoseLog.treatmentId`,
 * care e un id Long local Room fara sens pt. date care nu ajung niciodata local — vezi
 * LinkedPatientDataRepositoryImpl). */
data class LinkedDoseLog(val remoteId: String, val treatmentRemoteId: String, val log: DoseLog)

data class LinkedPatientData(
    val treatments: List<LinkedTreatment>,
    val doseLogs: List<LinkedDoseLog>
)
