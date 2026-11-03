package com.pillpronto.ui.settings

import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.model.Profile
import com.pillpronto.domain.usecase.GetProfileUseCase
import com.pillpronto.domain.usecase.ObserveAuthSessionUseCase
import com.pillpronto.util.FakeAuthRepository
import com.pillpronto.util.FakeProfileRepository
import com.pillpronto.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository()
    private val profileRepository = FakeProfileRepository()

    private fun createViewModel() = SettingsViewModel(
        ObserveAuthSessionUseCase(authRepository),
        GetProfileUseCase(profileRepository)
    )

    @Test
    fun `stare initiala delogata, fara profil`() {
        val vm = createViewModel()

        assertEquals(AuthSessionState.Unauthenticated, vm.state.value.sessionState)
        assertNull(vm.state.value.profile)
    }

    @Test
    fun `dupa autentificare, profilul e preluat automat`() = runTest {
        profileRepository.profiles["u1"] = Profile("u1", "Ana", isTrustedContributor = true)
        val vm = createViewModel()

        authRepository.emit(AuthSessionState.Authenticated("u1"))

        assertEquals(true, vm.state.value.profile?.isTrustedContributor)
    }

    @Test
    fun `delogare goleste profilul din stare`() = runTest {
        profileRepository.profiles["u1"] = Profile("u1", "Ana")
        val vm = createViewModel()
        authRepository.emit(AuthSessionState.Authenticated("u1"))
        assertEquals("Ana", vm.state.value.profile?.displayName)

        authRepository.emit(AuthSessionState.Unauthenticated)

        assertNull(vm.state.value.profile)
    }

    @Test
    fun `refresh reface profilul chiar daca sesiunea nu s-a schimbat`() = runTest {
        val vm = createViewModel()
        authRepository.emit(AuthSessionState.Authenticated("u1"))
        assertNull(vm.state.value.profile)

        profileRepository.profiles["u1"] = Profile("u1", "Maria", isTrustedContributor = true)
        vm.refresh()

        assertEquals(true, vm.state.value.profile?.isTrustedContributor)
    }

    @Test
    fun `refresh fara sesiune autentificata nu face nimic`() = runTest {
        val vm = createViewModel()

        vm.refresh()

        assertNull(vm.state.value.profile)
    }
}
