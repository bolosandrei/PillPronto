package com.pillpronto.ui.patients

import androidx.lifecycle.SavedStateHandle
import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.model.LinkedDoseLog
import com.pillpronto.domain.model.LinkedPatientData
import com.pillpronto.domain.model.LinkedTreatment
import com.pillpronto.domain.model.PatientSummary
import com.pillpronto.domain.model.Treatment
import com.pillpronto.domain.usecase.GetLinkedPatientDataUseCase
import com.pillpronto.domain.usecase.GetMyPatientsUseCase
import com.pillpronto.domain.usecase.ObserveAuthSessionUseCase
import com.pillpronto.ui.navigation.Route
import com.pillpronto.util.FakeAuthRepository
import com.pillpronto.util.FakeLinkRepository
import com.pillpronto.util.FakeLinkedPatientDataRepository
import com.pillpronto.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class PatientDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository()
    private val linkRepository = FakeLinkRepository()
    private val linkedPatientDataRepository = FakeLinkedPatientDataRepository()

    private fun createViewModel(patientProfileId: String = "patient-1") = PatientDetailViewModel(
        SavedStateHandle(mapOf(Route.PatientDetail.ARG to patientProfileId)),
        ObserveAuthSessionUseCase(authRepository),
        GetMyPatientsUseCase(linkRepository),
        GetLinkedPatientDataUseCase(linkedPatientDataRepository)
    )

    @Test
    fun `incarca numele, tratamentele si aderenta pacientului`() = runTest {
        linkRepository.patients["user-1"] = listOf(PatientSummary("patient-1", "Ana"))
        val treatment = Treatment(
            medicationName = "Metformin", dosage = "500 mg", times = emptyList(),
            startDate = LocalDate.of(2026, 8, 1)
        )
        linkedPatientDataRepository.dataByPatient["patient-1"] = LinkedPatientData(
            treatments = listOf(LinkedTreatment("t1", treatment)),
            doseLogs = listOf(
                LinkedDoseLog(
                    "d1", "t1",
                    DoseLog(treatmentId = 0L, scheduledAt = LocalDateTime.of(2026, 8, 1, 8, 0), status = DoseStatus.TAKEN)
                )
            )
        )

        val vm = createViewModel()
        authRepository.emit(AuthSessionState.Authenticated("user-1"))

        assertEquals("Ana", vm.state.value.displayName)
        assertEquals(1, vm.state.value.treatments.size)
        assertEquals(1.0, vm.state.value.stats.pdc, 0.0001)
        assertEquals(false, vm.state.value.isLoading)
    }

    @Test
    fun `pacient necunoscut in lista mea ramane fara nume, fara sa crape`() = runTest {
        linkRepository.patients["user-1"] = emptyList()

        val vm = createViewModel("necunoscut")
        authRepository.emit(AuthSessionState.Authenticated("user-1"))

        assertEquals(null, vm.state.value.displayName)
        assertEquals(false, vm.state.value.isLoading)
    }
}
