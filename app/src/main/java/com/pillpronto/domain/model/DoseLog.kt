package com.pillpronto.domain.model

import java.time.LocalDateTime

/** O doza programata si statusul ei — sursa de date pentru metricile de aderenta. */
data class DoseLog(
    val id: Long = 0L,
    val treatmentId: Long,
    val scheduledAt: LocalDateTime,
    val status: DoseStatus = DoseStatus.PENDING,
    val takenAt: LocalDateTime? = null
)
