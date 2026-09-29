package com.pillpronto.domain.model

import java.time.LocalDateTime

/** O intrare din trail-ul de audit al Pacientului (Faza 1.5f) — cine i-a vazut datele si cand.
 * `actorDisplayName` null daca profilul actorului nu s-a putut rezolva (ex. cont sters intre timp). */
data class AuditLogEntry(
    val actorUserId: String,
    val actorDisplayName: String?,
    val action: String,
    val entity: String?,
    val occurredAt: LocalDateTime
)
