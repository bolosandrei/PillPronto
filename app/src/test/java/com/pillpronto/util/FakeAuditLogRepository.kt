package com.pillpronto.util

import com.pillpronto.domain.model.AuditLogEntry
import com.pillpronto.domain.repository.AuditLogRepository

/** Fake simplu pentru testarea use-case-urilor de audit (Faza 1.5f). */
class FakeAuditLogRepository : AuditLogRepository {

    val recordedAccesses = mutableListOf<Pair<String, String>>() // (patientProfileId, actorUserId)
    var recordAccessError: Throwable? = null
    val entriesByPatient = mutableMapOf<String, List<AuditLogEntry>>()
    var getMyAuditLogError: Throwable? = null

    override suspend fun recordAccess(patientProfileId: String, actorUserId: String) {
        recordAccessError?.let { throw it }
        recordedAccesses.add(patientProfileId to actorUserId)
    }

    override suspend fun getMyAuditLog(patientProfileId: String): List<AuditLogEntry> {
        getMyAuditLogError?.let { throw it }
        return entriesByPatient[patientProfileId].orEmpty()
    }
}
