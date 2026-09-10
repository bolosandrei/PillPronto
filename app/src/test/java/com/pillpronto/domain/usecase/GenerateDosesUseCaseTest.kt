package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.DoseSlot
import com.pillpronto.domain.model.Treatment
import com.pillpronto.util.FakeDoseRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class GenerateDosesUseCaseTest {

    private val repository = FakeDoseRepository()
    private val useCase = GenerateDosesUseCase(repository)

    @Test
    fun `fiecare slot primeste cantitatea proprie, sloturile fara cantitate proprie mostenesc cantitatea generala`() = runTest {
        val treatment = Treatment(
            id = 1,
            medicationName = "Nolpaza",
            dosage = "40 mg",
            cantitate = "1 comprimat", // cantitate generala, mostenita de sloturile fara cantitate proprie
            schedule = listOf(
                DoseSlot(LocalTime.of(8, 0)),               // fara cantitate proprie -> mosteneste "1 comprimat"
                DoseSlot(LocalTime.of(20, 0), "2 comprimate") // cantitate proprie, diferita
            ),
            startDate = LocalDate.now().minusDays(1),
            endDate = LocalDate.now().plusDays(1)
        )

        useCase(treatment, horizonDays = 5)

        val generated = repository.getLogsBetween(LocalDate.now().atStartOfDay(), LocalDate.now().plusDays(5).atStartOfDay())
        val morning = generated.first { it.scheduledAt.toLocalTime() == LocalTime.of(8, 0) }
        val evening = generated.first { it.scheduledAt.toLocalTime() == LocalTime.of(20, 0) }
        assertEquals("1 comprimat", morning.cantitate)
        assertEquals("2 comprimate", evening.cantitate)
    }

    @Test
    fun `fara slot la o anumita ora, nicio doza nu se genereaza la acea ora`() = runTest {
        val treatment = Treatment(
            id = 1,
            medicationName = "Nolpaza",
            dosage = "40 mg",
            schedule = listOf(DoseSlot(LocalTime.of(8, 0)), DoseSlot(LocalTime.of(20, 0))),
            startDate = LocalDate.now().minusDays(1),
            endDate = LocalDate.now().plusDays(1)
        )

        useCase(treatment, horizonDays = 5)

        val generated = repository.getLogsBetween(LocalDate.now().atStartOfDay(), LocalDate.now().plusDays(5).atStartOfDay())
        assertEquals(0, generated.count { it.scheduledAt.toLocalTime() == LocalTime.of(13, 0) })
    }
}
