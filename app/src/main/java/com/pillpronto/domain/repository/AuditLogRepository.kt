package com.pillpronto.domain.repository

import com.pillpronto.domain.model.AuditLogEntry

/** Trail-ul de audit "cine imi vede datele" (Faza 1.5f, GDPR) — un rand per citire a datelor unui
 * pacient legat de catre un Apartinator/Medic/Farmacist. */
interface AuditLogRepository {
    /** Scrie un rand nou — apelat explicit din ViewModel-urile care afiseaza date ale unui
     * pacient legat (NU dintr-un worker de fundal, vezi RecordDataAccessUseCase). RLS
     * (`audit_log_insert`, migrarea 0014) cere ca actorul sa aiba o legatura ACCEPTATA cu
     * `patientProfileId` — un apel fara legatura reala esueaza silentios (vezi use-case-ul). */
    suspend fun recordAccess(patientProfileId: String, actorUserId: String)

    /** Pacient: propriul trail de audit, cel mai recent primul — RLS (`audit_log_owner_read`)
     * scopeaza deja la owner-ul profilului. */
    suspend fun getMyAuditLog(patientProfileId: String): List<AuditLogEntry>
}
