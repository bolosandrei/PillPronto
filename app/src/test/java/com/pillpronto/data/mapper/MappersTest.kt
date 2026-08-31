package com.pillpronto.data.mapper

import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.model.Treatment
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MappersTest {

    @Test
    fun `Treatment round-trip pastreaza datele`() {
        val original = Treatment(
            id = 5,
            medicationName = "Augmentin",
            dosage = "500 mg",
            times = listOf(LocalTime.of(20, 0), LocalTime.of(8, 0)),
            startDate = LocalDate.of(2026, 8, 1),
            endDate = LocalDate.of(2026, 8, 31),
            active = true
        )
        val restored = original.toEntity().toDomain()
        assertEquals(original.medicationName, restored.medicationName)
        assertEquals(original.dosage, restored.dosage)
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), restored.times) // sortate
        assertEquals(original.startDate, restored.startDate)
        assertEquals(original.endDate, restored.endDate)
    }

    @Test
    fun `DoseLog round-trip pastreaza statusul si timpul`() {
        val original = DoseLog(
            id = 3,
            treatmentId = 5,
            scheduledAt = LocalDateTime.of(2026, 8, 20, 8, 0),
            status = DoseStatus.TAKEN,
            takenAt = LocalDateTime.of(2026, 8, 20, 8, 15)
        )
        val restored = original.toEntity().toDomain()
        assertEquals(original.scheduledAt, restored.scheduledAt)
        assertEquals(original.status, restored.status)
        assertEquals(original.takenAt, restored.takenAt)
    }
}
