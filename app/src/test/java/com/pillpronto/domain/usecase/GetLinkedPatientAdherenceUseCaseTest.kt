package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.model.LinkedDoseLog
import com.pillpronto.domain.model.LinkedPatientData
import com.pillpronto.util.FakeLinkedPatientDataRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class GetLinkedPatientAdherenceUseCaseTest {

    @Test
    fun `calculeaza aderenta din dozele remote ale pacientului legat`() = runTest {
        val repo = FakeLinkedPatientDataRepository()
        val log = DoseLog(treatmentId = 0L, scheduledAt = LocalDateTime.of(2026, 8, 18, 8, 0), status = DoseStatus.TAKEN)
        repo.dataByPatient["patient-1"] = LinkedPatientData(
            treatments = emptyList(),
            doseLogs = listOf(LinkedDoseLog(remoteId = "d1", treatmentRemoteId = "t1", log = log))
        )
        val uc = GetLinkedPatientAdherenceUseCase(repo)

        val stats = uc("patient-1")

        assertEquals(1.0, stats.pdc, 0.0001)
        assertEquals(1.0, stats.mpr, 0.0001)
    }

    @Test
    fun `fara date pentru pacient, aderenta EMPTY`() = runTest {
        val repo = FakeLinkedPatientDataRepository()
        val uc = GetLinkedPatientAdherenceUseCase(repo)

        val stats = uc("necunoscut")

        assertEquals(0.0, stats.pdc, 0.0001)
    }
}
