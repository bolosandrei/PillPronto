package com.pillpronto.ui.patients

import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.model.LinkedDoseLog
import com.pillpronto.domain.model.LinkedPatientData
import com.pillpronto.domain.model.PatientSummary
import com.pillpronto.domain.usecase.ClaimInviteUseCase
import com.pillpronto.domain.usecase.GetLinkedPatientAdherenceUseCase
import com.pillpronto.domain.usecase.GetMyPatientsUseCase
import com.pillpronto.domain.usecase.ObserveAuthSessionUseCase
import com.pillpronto.util.FakeAuthRepository
import com.pillpronto.util.FakeLinkRepository
import com.pillpronto.util.FakeLinkedPatientDataRepository
import com.pillpronto.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

class MyPatientsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository()
    private val linkRepository = FakeLinkRepository()
    private val linkedPatientDataRepository = FakeLinkedPatientDataRepository()

    private fun createViewModel() = MyPatientsViewModel(
        ObserveAuthSessionUseCase(authRepository),
        ClaimInviteUseCase(linkRepository),
        GetMyPatientsUseCase(linkRepository),
        GetLinkedPatientAdherenceUseCase(linkedPatientDataRepository)
    )

    @Test
    fun `la autentificare incarca pacientii legati cu aderenta lor`() = runTest {
        linkRepository.patients["user-1"] = listOf(PatientSummary("patient-1", "Ana"))
        linkedPatientDataRepository.dataByPatient["patient-1"] = LinkedPatientData(
            treatments = emptyList(),
            doseLogs = listOf(
                LinkedDoseLog(
                    "d1", "t1",
                    DoseLog(treatmentId = 0L, scheduledAt = LocalDateTime.of(2026, 8, 1, 8, 0), status = DoseStatus.TAKEN)
                )
            )
        )

        val vm = createViewModel()
        authRepository.emit(AuthSessionState.Authenticated("user-1"))

        assertEquals(1, vm.state.value.patients.size)
        assertEquals("Ana", vm.state.value.patients.first().displayName)
        assertEquals(1.0, vm.state.value.patients.first().stats.pdc, 0.0001)
    }

    @Test
    fun `revendicarea unui cod valid goleste inputul si reface lista`() = runTest {
        val vm = createViewModel()
        authRepository.emit(AuthSessionState.Authenticated("user-1"))
        vm.onCodeChange("ABC123")

        vm.onClaim()

        assertEquals("ABC123", linkRepository.lastClaimedCode)
        assertEquals("", vm.state.value.codeInput)
        assertEquals(false, vm.state.value.claimError)
    }

    @Test
    fun `revendicarea unui cod invalid seteaza claimError`() = runTest {
        linkRepository.claimInviteResult = Result.failure(RuntimeException("cod invalid"))
        val vm = createViewModel()
        authRepository.emit(AuthSessionState.Authenticated("user-1"))
        vm.onCodeChange("BAD")

        vm.onClaim()

        assertTrue(vm.state.value.claimError)
        assertEquals("BAD", vm.state.value.codeInput) // pastrat, userul poate corecta
    }

    @Test
    fun `cod gol nu declanseaza revendicarea`() = runTest {
        val vm = createViewModel()
        authRepository.emit(AuthSessionState.Authenticated("user-1"))

        vm.onClaim()

        assertEquals(null, linkRepository.lastClaimedCode)
    }
}
