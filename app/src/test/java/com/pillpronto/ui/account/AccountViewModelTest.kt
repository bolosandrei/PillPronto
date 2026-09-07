package com.pillpronto.ui.account

import com.pillpronto.domain.model.AccountRole
import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.model.Profile
import com.pillpronto.domain.usecase.GetProfileUseCase
import com.pillpronto.domain.usecase.ObserveAuthSessionUseCase
import com.pillpronto.domain.usecase.SignInUseCase
import com.pillpronto.domain.usecase.SignOutUseCase
import com.pillpronto.domain.usecase.SignUpUseCase
import com.pillpronto.util.FakeAuthRepository
import com.pillpronto.util.FakeProfileRepository
import com.pillpronto.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class AccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository()
    private val profileRepository = FakeProfileRepository()

    private fun createViewModel() = AccountViewModel(
        ObserveAuthSessionUseCase(authRepository),
        SignUpUseCase(authRepository),
        SignInUseCase(authRepository),
        SignOutUseCase(authRepository),
        GetProfileUseCase(profileRepository)
    )

    @Test
    fun `submit cu email gol nu apeleaza sign-in si seteaza eroarea potrivita`() {
        val vm = createViewModel()

        vm.submit()

        assertEquals(AccountError.EMPTY_EMAIL, vm.state.value.error)
        assertNull(authRepository.lastSignInEmail)
    }

    @Test
    fun `submit cu email invalid seteaza eroarea potrivita`() {
        val vm = createViewModel()
        vm.onEmailChange("nu-e-email")
        vm.onPasswordChange("parola123")

        vm.submit()

        assertEquals(AccountError.INVALID_EMAIL, vm.state.value.error)
    }

    @Test
    fun `submit cu parola prea scurta seteaza eroarea potrivita`() {
        val vm = createViewModel()
        vm.onEmailChange("test@example.com")
        vm.onPasswordChange("123")

        vm.submit()

        assertEquals(AccountError.PASSWORD_TOO_SHORT, vm.state.value.error)
    }

    @Test
    fun `submit la inregistrare cu parole diferite seteaza eroarea potrivita`() {
        val vm = createViewModel()
        vm.onToggleMode() // SIGN_IN -> SIGN_UP
        vm.onEmailChange("test@example.com")
        vm.onPasswordChange("parola123")
        vm.onConfirmPasswordChange("altaparola")

        vm.submit()

        assertEquals(AccountError.PASSWORDS_DO_NOT_MATCH, vm.state.value.error)
    }

    @Test
    fun `submit valid la autentificare apeleaza signInWithEmail`() = runTest {
        val vm = createViewModel()
        vm.onEmailChange("test@example.com")
        vm.onPasswordChange("parola123")

        vm.submit()

        assertEquals("test@example.com", authRepository.lastSignInEmail)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `submit valid la inregistrare apeleaza signUpWithEmail si arata mesajul de confirmare`() = runTest {
        val vm = createViewModel()
        vm.onToggleMode()
        vm.onEmailChange("test@example.com")
        vm.onPasswordChange("parola123")
        vm.onConfirmPasswordChange("parola123")

        vm.submit()

        assertEquals("test@example.com", authRepository.lastSignUpEmail)
        assertEquals(true, vm.state.value.signUpAwaitingConfirmation)
    }

    @Test
    fun `esec la autentificare seteaza AUTH_FAILED`() = runTest {
        authRepository.signInError = RuntimeException("boom")
        val vm = createViewModel()
        vm.onEmailChange("test@example.com")
        vm.onPasswordChange("parola123")

        vm.submit()

        assertEquals(AccountError.AUTH_FAILED, vm.state.value.error)
    }

    @Test
    fun `limita de emailuri Supabase seteaza RATE_LIMITED, nu AUTH_FAILED`() = runTest {
        authRepository.signUpError = RuntimeException("over_email_send_rate_limit")
        val vm = createViewModel()
        vm.onToggleMode()
        vm.onEmailChange("test@example.com")
        vm.onPasswordChange("parola123")
        vm.onConfirmPasswordChange("parola123")

        vm.submit()

        assertEquals(AccountError.RATE_LIMITED, vm.state.value.error)
    }

    @Test
    fun `dupa autentificare, profilul e preluat automat`() = runTest {
        profileRepository.profiles["u1"] = Profile("u1", AccountRole.PATIENT, "Ana")
        val vm = createViewModel()

        authRepository.emit(AuthSessionState.Authenticated("u1"))

        assertEquals(true, vm.state.value.profileChecked)
        assertEquals("Ana", vm.state.value.profile?.displayName)
    }
}
