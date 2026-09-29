package com.pillpronto.domain.usecase

import com.pillpronto.domain.repository.AuditLogRepository
import javax.inject.Inject

/** Inregistreaza in trail-ul de audit ca `actorUserId` tocmai a vazut datele lui `patientProfileId`
 * (Faza 1.5f). Fire-and-forget deliberat — un eșec (ex. legatura nu mai e acceptata) NU trebuie sa
 * blocheze afisarea datelor deja citite, doar sa lipseasca din audit. */
class RecordDataAccessUseCase @Inject constructor(
    private val auditLogRepository: AuditLogRepository
) {
    suspend operator fun invoke(patientProfileId: String, actorUserId: String) {
        runCatching { auditLogRepository.recordAccess(patientProfileId, actorUserId) }
    }
}
