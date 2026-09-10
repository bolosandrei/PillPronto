package com.pillpronto.data.mapper

import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.model.DoseSlot
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
            schedule = listOf(DoseSlot(LocalTime.of(20, 0)), DoseSlot(LocalTime.of(8, 0))),
            startDate = LocalDate.of(2026, 8, 1),
            endDate = LocalDate.of(2026, 8, 31),
            active = true,
            formaFarmaceutica = "COMPR. FILM.",
            cantitate = "2 comprimate",
            indicatie = "infecție respiratorie",
            instructiuni = "cu mâncare",
            codCim = "W43451001"
        )
        val restored = original.toEntity("test-patient").toDomain()
        assertEquals(original.medicationName, restored.medicationName)
        assertEquals(original.dosage, restored.dosage)
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), restored.times) // sortate
        assertEquals(original.startDate, restored.startDate)
        assertEquals(original.endDate, restored.endDate)
        assertEquals(original.formaFarmaceutica, restored.formaFarmaceutica)
        assertEquals(original.cantitate, restored.cantitate)
        assertEquals(original.indicatie, restored.indicatie)
        assertEquals(original.instructiuni, restored.instructiuni)
        assertEquals(original.codCim, restored.codCim)
    }

    @Test
    fun `Treatment cu cantitate diferita per slot pastreaza asocierea ora-cantitate`() {
        val original = Treatment(
            medicationName = "Nolpaza",
            dosage = "40 mg",
            schedule = listOf(
                DoseSlot(LocalTime.of(20, 0), "2 comprimate"),
                DoseSlot(LocalTime.of(8, 0), "1 comprimat")
            ),
            startDate = LocalDate.of(2026, 8, 1)
        )
        val restored = original.toEntity("test-patient").toDomain()

        val morning = restored.schedule.first { it.time == LocalTime.of(8, 0) }
        val evening = restored.schedule.first { it.time == LocalTime.of(20, 0) }
        assertEquals("1 comprimat", morning.cantitate)
        assertEquals("2 comprimate", evening.cantitate)
    }

    @Test
    fun `DoseLog round-trip pastreaza statusul, timpul si cantitatea`() {
        val original = DoseLog(
            id = 3,
            treatmentId = 5,
            scheduledAt = LocalDateTime.of(2026, 8, 20, 8, 0),
            status = DoseStatus.TAKEN,
            takenAt = LocalDateTime.of(2026, 8, 20, 8, 15),
            cantitate = "1 comprimat"
        )
        val restored = original.toEntity("test-patient").toDomain()
        assertEquals(original.scheduledAt, restored.scheduledAt)
        assertEquals(original.status, restored.status)
        assertEquals(original.takenAt, restored.takenAt)
        assertEquals(original.cantitate, restored.cantitate)
    }
}
