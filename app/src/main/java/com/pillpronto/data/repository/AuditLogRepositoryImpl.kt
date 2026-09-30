package com.pillpronto.data.repository

import com.pillpronto.data.remote.dto.AuditLogDto
import com.pillpronto.data.remote.dto.AuditLogInsertDto
import com.pillpronto.data.remote.dto.ProfileDto
import com.pillpronto.domain.model.AuditLogEntry
import com.pillpronto.domain.repository.AuditLogRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class AuditLogRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : AuditLogRepository {

    override suspend fun recordAccess(patientProfileId: String, actorUserId: String) {
        supabase.from(AUDIT_LOG_TABLE).insert(
            AuditLogInsertDto(
                actorUserId = actorUserId,
                patientProfileId = patientProfileId,
                action = ACTION_VIEW,
                entity = ENTITY_PATIENT_DATA
            )
        )
    }

    override suspend fun getMyAuditLog(patientProfileId: String): List<AuditLogEntry> {
        val rows = supabase.from(AUDIT_LOG_TABLE)
            .select { filter { eq("patient_profile_id", patientProfileId) } }
            .decodeList<AuditLogDto>()
            .sortedByDescending { it.occurredAt }
        if (rows.isEmpty()) return emptyList()

        val actorIds = rows.map { it.actorUserId }.distinct()
        val namesByActor = supabase.from(PROFILES_TABLE)
            .select { filter { isIn("id", actorIds) } }
            .decodeList<ProfileDto>()
            .associate { it.id to it.displayName }

        return rows.map { dto ->
            AuditLogEntry(
                actorUserId = dto.actorUserId,
                actorDisplayName = namesByActor[dto.actorUserId],
                action = dto.action,
                entity = dto.entity,
                occurredAt = Instant.parse(dto.occurredAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
            )
        }
    }

    private companion object {
        const val AUDIT_LOG_TABLE = "audit_log"
        const val PROFILES_TABLE = "profiles"
        const val ACTION_VIEW = "view"
        const val ENTITY_PATIENT_DATA = "patient_data"
    }
}
