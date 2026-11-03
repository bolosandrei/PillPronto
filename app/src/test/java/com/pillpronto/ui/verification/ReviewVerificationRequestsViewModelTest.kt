package com.pillpronto.ui.verification

import com.pillpronto.domain.model.ClinicianType
import com.pillpronto.domain.model.PendingClinicianVerification
import com.pillpronto.domain.usecase.ApproveClinicianVerificationUseCase
import com.pillpronto.domain.usecase.ListPendingClinicianVerificationsUseCase
import com.pillpronto.domain.usecase.RejectClinicianVerificationUseCase
import com.pillpronto.util.FakeProfileRepository
import com.pillpronto.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReviewVerificationRequestsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val profileRepository = FakeProfileRepository()

    private fun createViewModel() = ReviewVerificationRequestsViewModel(
        ListPendingClinicianVerificationsUseCase(profileRepository),
        ApproveClinicianVerificationUseCase(profileRepository),
        RejectClinicianVerificationUseCase(profileRepository)
    )

    @Test
    fun `la pornire incarca cererile in asteptare`() = runTest {
        profileRepository.pendingVerifications = listOf(
            PendingClinicianVerification("u1", "Ana", "CUIM123", "Ana Popescu", ClinicianType.DOCTOR)
        )

        val vm = createViewModel()

        assertEquals(1, vm.state.value.requests.size)
        assertEquals("u1", vm.state.value.requests.first().userId)
    }

    @Test
    fun `onApprove apeleaza use-case-ul si reface lista`() = runTest {
        profileRepository.pendingVerifications = listOf(
            PendingClinicianVerification("u1", "Ana", "CUIM123", "Ana Popescu", ClinicianType.DOCTOR)
        )
        val vm = createViewModel()

        vm.onApprove("u1")

        assertEquals("u1", profileRepository.lastApproveCall)
        assertEquals(null, vm.state.value.processingUserId)
    }

    @Test
    fun `onReject apeleaza use-case-ul si reface lista`() = runTest {
        profileRepository.pendingVerifications = listOf(
            PendingClinicianVerification("u1", "Ana", "CUIM123", "Ana Popescu", ClinicianType.DOCTOR)
        )
        val vm = createViewModel()

        vm.onReject("u1")

        assertEquals("u1", profileRepository.lastRejectCall)
    }
}
