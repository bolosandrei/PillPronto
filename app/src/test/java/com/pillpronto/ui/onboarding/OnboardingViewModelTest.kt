package com.pillpronto.ui.onboarding

import com.pillpronto.domain.model.AccountRole
import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.usecase.CompleteOnboardingUseCase
import com.pillpronto.domain.usecase.ObserveAuthSessionUseCase
import com.pillpronto.util.FakeAuthRepository
import com.pillpronto.util.FakeProfileRepository
import com.pillpronto.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository()
    private val profileRepository = FakeProfileRepository()

    private fun createViewModel() = OnboardingViewModel(
        ObserveAuthSessionUseCase(authRepository),
        CompleteOnboardingUseCase(profileRepository)
    )

    @Test
    fun `submit fara rol selectat seteaza NO_ROLE`() {
        val vm = createViewModel()
        vm.onDisplayNameChange("Ana")

        vm.submit()

        assertEquals(OnboardingError.NO_ROLE, vm.state.value.error)
    }

    @Test
    fun `submit cu nume gol seteaza EMPTY_NAME`() {
        val vm = createViewModel()
        vm.onRoleSelected(AccountRole.PATIENT)

        vm.submit()

        assertEquals(OnboardingError.EMPTY_NAME, vm.state.value.error)
    }

    @Test
    fun `submit fara sesiune autentificata seteaza NOT_AUTHENTICATED`() {
        val vm = createViewModel()
        vm.onRoleSelected(AccountRole.PATIENT)
        vm.onDisplayNameChange("Ana")

        vm.submit()

        assertEquals(OnboardingError.NOT_AUTHENTICATED, vm.state.value.error)
    }

    @Test
    fun `submit valid completeaza onboarding-ul si marcheaza done`() = runTest {
        val vm = createViewModel()
        authRepository.emit(AuthSessionState.Authenticated("u1"))
        vm.onRoleSelected(AccountRole.CAREGIVER)
        vm.onDisplayNameChange("Maria")

        vm.submit()

        assertEquals(true, vm.state.value.done)
        assertNull(vm.state.value.error)
        assertEquals(Triple("u1", AccountRole.CAREGIVER, "Maria"), profileRepository.lastCompleteOnboardingCall)
    }

    @Test
    fun `esec la salvare seteaza SAVE_FAILED`() = runTest {
        profileRepository.completeOnboardingError = RuntimeException("boom")
        val vm = createViewModel()
        authRepository.emit(AuthSessionState.Authenticated("u1"))
        vm.onRoleSelected(AccountRole.PATIENT)
        vm.onDisplayNameChange("Ana")

        vm.submit()

        assertEquals(OnboardingError.SAVE_FAILED, vm.state.value.error)
    }
}
