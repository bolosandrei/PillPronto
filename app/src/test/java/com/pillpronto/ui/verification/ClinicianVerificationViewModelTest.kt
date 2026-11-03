package com.pillpronto.ui.verification

import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.model.ClinicianType
import com.pillpronto.domain.model.Profile
import com.pillpronto.domain.usecase.GetProfileUseCase
import com.pillpronto.domain.usecase.ObserveAuthSessionUseCase
import com.pillpronto.domain.usecase.SubmitClinicianVerificationRequestUseCase
import com.pillpronto.util.FakeAuthRepository
import com.pillpronto.util.FakeProfileRepository
import com.pillpronto.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ClinicianVerificationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository()
    private val profileRepository = FakeProfileRepository()

    private fun createViewModel() = ClinicianVerificationViewModel(
        ObserveAuthSessionUseCase(authRepository),
        GetProfileUseCase(profileRepository),
        SubmitClinicianVerificationRequestUseCase(profileRepository)
    )

    @Test
    fun `onSubmit cu campuri goale nu trimite nimic`() = runTest {
        profileRepository.profiles["u1"] = Profile("u1", "Ana")
        val vm = createViewModel()
        authRepository.emit(AuthSessionState.Authenticated("u1"))
        vm.onTypeChange(ClinicianType.DOCTOR)

        vm.onSubmit()

        assertNull(profileRepository.lastVerificationRequestCall)
    }

    @Test
    fun `onSubmit valid trimite cererea, o forteaza pe is_verified_clinician=false si reface profilul`() = runTest {
        profileRepository.profiles["u1"] = Profile("u1", "Ana")
        val vm = createViewModel()
        authRepository.emit(AuthSessionState.Authenticated("u1"))
        vm.onTypeChange(ClinicianType.DOCTOR)
        vm.onCuimChange("CUIM123")
        vm.onFullNameChange("Ana Popescu")

        vm.onSubmit()

        assertEquals(
            FakeProfileRepository.VerificationRequestCall("u1", "CUIM123", "Ana Popescu", ClinicianType.DOCTOR),
            profileRepository.lastVerificationRequestCall
        )
        assertEquals(ClinicianType.DOCTOR, vm.state.value.profile?.clinicianVerificationType)
        assertEquals(false, vm.state.value.profile?.isVerifiedClinician)
        assertEquals(false, vm.state.value.showForm)
    }

    @Test
    fun `onShowForm pre-completeaza campurile din ultima cerere trimisa`() = runTest {
        profileRepository.profiles["u1"] = Profile(
            "u1", "Ana",
            clinicianVerificationCuim = "CUIM999",
            clinicianVerificationFullName = "Ana Popescu",
            clinicianVerificationType = ClinicianType.PHARMACIST,
            isVerifiedClinician = true
        )
        val vm = createViewModel()
        authRepository.emit(AuthSessionState.Authenticated("u1"))

        vm.onShowForm()

        assertEquals(true, vm.state.value.showForm)
        assertEquals("CUIM999", vm.state.value.cuimInput)
        assertEquals("Ana Popescu", vm.state.value.fullNameInput)
        assertEquals(ClinicianType.PHARMACIST, vm.state.value.typeInput)
    }

    @Test
    fun `resubmisia dupa respingere sterge clinicianVerificationRejected`() = runTest {
        profileRepository.profiles["u1"] = Profile(
            "u1", "Ana",
            clinicianVerificationCuim = "CUIM999",
            clinicianVerificationFullName = "Ana Popescu",
            clinicianVerificationType = ClinicianType.PHARMACIST,
            clinicianVerificationRejected = true
        )
        val vm = createViewModel()
        authRepository.emit(AuthSessionState.Authenticated("u1"))
        vm.onTypeChange(ClinicianType.PHARMACIST)
        vm.onCuimChange("CUIM999-NOU")
        vm.onFullNameChange("Ana Popescu")

        vm.onSubmit()

        assertEquals(false, vm.state.value.profile?.clinicianVerificationRejected)
    }

    @Test
    fun `profilul se incarca automat la autentificare`() = runTest {
        profileRepository.profiles["u1"] = Profile("u1", "Ana", isVerifiedClinician = true, clinicianVerificationType = ClinicianType.DOCTOR)
        val vm = createViewModel()

        authRepository.emit(AuthSessionState.Authenticated("u1"))

        assertEquals(true, vm.state.value.profile?.isVerifiedClinician)
    }
}
