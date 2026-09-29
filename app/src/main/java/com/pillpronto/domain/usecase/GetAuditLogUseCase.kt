package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.AuditLogEntry
import com.pillpronto.domain.repository.AuditLogRepository
import javax.inject.Inject

/** Pacient: propriul trail de audit ("Cine imi vede datele", Faza 1.5f). */
class GetAuditLogUseCase @Inject constructor(
    private val auditLogRepository: AuditLogRepository
) {
    suspend operator fun invoke(patientProfileId: String): List<AuditLogEntry> =
        auditLogRepository.getMyAuditLog(patientProfileId)
}
