package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.model.Treatment
import com.pillpronto.util.FakeDoseRepository
import com.pillpronto.util.FakeTreatmentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ResolveDoseStatusForCodCimUseCaseTest {

    private val treatmentRepository = FakeTreatmentRepository()
    private val doseRepository = FakeDoseRepository()
    private val useCase = ResolveDoseStatusForCodCimUseCase(treatmentRepository, doseRepository)

    private fun treatment(codCim: String, id: Long = 1L, active: Boolean = true) = Treatment(
        id = id,
        medicationName = "Paracetamol",
        dosage = "500 mg",
        startDate = LocalDate.now(),
        codCim = codCim,
        active = active
    )

    @Test
    fun `niciun tratament cu acest codCim intoarce null`() = runTest {
        treatmentRepository.setTreatments(listOf(treatment("ALTUL")))

        assertNull(useCase("COD123"))
    }

    @Test
    fun `tratament gasit fara nicio doza azi intoarce null`() = runTest {
        treatmentRepository.setTreatments(listOf(treatment("COD123")))
        doseRepository.setLogs(emptyList())

        assertNull(useCase("COD123"))
    }

    @Test
    fun `doza TAKEN azi intoarce statusul corect`() = runTest {
        val now = LocalDateTime.now()
        treatmentRepository.setTreatments(listOf(treatment("COD123")))
        doseRepository.setLogs(listOf(DoseLog(id = 1, treatmentId = 1, scheduledAt = now, status = DoseStatus.TAKEN)))

        val result = useCase("COD123", now)

        assertEquals(DoseStatus.TAKEN, result?.status)
    }

    @Test
    fun `doza PENDING in fereastra de actiune e marcata actionabila`() = runTest {
        val now = LocalDateTime.now()
        treatmentRepository.setTreatments(listOf(treatment("COD123")))
        doseRepository.setLogs(listOf(
            DoseLog(id = 1, treatmentId = 1, scheduledAt = now.minusMinutes(10), status = DoseStatus.PENDING)
        ))

        val result = useCase("COD123", now)

        assertEquals(DoseStatus.PENDING, result?.status)
        assertEquals(true, result?.isActionable)
    }

    @Test
    fun `doza PENDING departe in viitor NU e actionabila`() = runTest {
        val now = LocalDateTime.now()
        treatmentRepository.setTreatments(listOf(treatment("COD123")))
        doseRepository.setLogs(listOf(
            DoseLog(id = 1, treatmentId = 1, scheduledAt = now.plusHours(6), status = DoseStatus.PENDING)
        ))

        val result = useCase("COD123", now)

        assertEquals(false, result?.isActionable)
    }

    @Test
    fun `alege doza cea mai apropiata de acum cand sunt mai multe azi`() = runTest {
        val now = LocalDateTime.now()
        treatmentRepository.setTreatments(listOf(treatment("COD123")))
        doseRepository.setLogs(listOf(
            DoseLog(id = 1, treatmentId = 1, scheduledAt = now.minusHours(5), status = DoseStatus.TAKEN),
            DoseLog(id = 2, treatmentId = 1, scheduledAt = now.plusMinutes(20), status = DoseStatus.PENDING)
        ))

        val result = useCase("COD123", now)

        assertEquals(DoseStatus.PENDING, result?.status) // doza 2 e mai aproape de "now"
    }

    @Test
    fun `tratament inactiv nu e considerat`() = runTest {
        treatmentRepository.setTreatments(listOf(treatment("COD123", active = false)))

        assertNull(useCase("COD123"))
    }
}
