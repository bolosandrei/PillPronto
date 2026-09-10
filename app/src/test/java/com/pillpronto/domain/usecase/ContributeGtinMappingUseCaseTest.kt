package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.AccountRole
import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.model.Profile
import com.pillpronto.util.FakeAuthRepository
import com.pillpronto.util.FakeGtinCatalogRemoteDataSource
import com.pillpronto.util.FakeGtinMappingRepository
import com.pillpronto.util.FakeProfileRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContributeGtinMappingUseCaseTest {

    private val gtinMappingRepository = FakeGtinMappingRepository()
    private val authRepository = FakeAuthRepository()
    private val profileRepository = FakeProfileRepository()
    private val remoteDataSource = FakeGtinCatalogRemoteDataSource()
    private val useCase = ContributeGtinMappingUseCase(
        confirmGtinMapping = ConfirmGtinMappingUseCase(gtinMappingRepository),
        authRepository = authRepository,
        profileRepository = profileRepository,
        remoteDataSource = remoteDataSource
    )

    @Test
    fun `user neautentificat salveaza doar local, fara apel remote`() = runTest {
        authRepository.emit(AuthSessionState.Unauthenticated)

        val contributed = useCase("05901234123457", "W43451001")

        assertFalse(contributed)
        assertEquals("W43451001", gtinMappingRepository.mappings["05901234123457"])
        assertNull(remoteDataSource.lastContributed)
    }

    @Test
    fun `user autentificat dar fara flag salveaza doar local`() = runTest {
        authRepository.emit(AuthSessionState.Authenticated("user-1"))
        profileRepository.profiles["user-1"] = Profile("user-1", AccountRole.PATIENT, "Test", isTrustedContributor = false)

        val contributed = useCase("05901234123457", "W43451001")

        assertFalse(contributed)
        assertEquals("W43451001", gtinMappingRepository.mappings["05901234123457"])
        assertNull(remoteDataSource.lastContributed)
    }

    @Test
    fun `contribuitor de incredere salveaza local si propaga la catalogul comun`() = runTest {
        authRepository.emit(AuthSessionState.Authenticated("user-1"))
        profileRepository.profiles["user-1"] = Profile("user-1", AccountRole.PHARMACIST, "Test", isTrustedContributor = true)

        val contributed = useCase("05901234123457", "W43451001")

        assertTrue(contributed)
        assertEquals("W43451001", gtinMappingRepository.mappings["05901234123457"])
        assertEquals("05901234123457" to "W43451001", remoteDataSource.lastContributed)
    }

    @Test
    fun `esecul apelului remote nu anuleaza confirmarea locala`() = runTest {
        authRepository.emit(AuthSessionState.Authenticated("user-1"))
        profileRepository.profiles["user-1"] = Profile("user-1", AccountRole.PHARMACIST, "Test", isTrustedContributor = true)
        remoteDataSource.contributeResult = Result.failure(RuntimeException("network down"))

        val contributed = useCase("05901234123457", "W43451001")

        assertFalse(contributed)
        assertEquals("W43451001", gtinMappingRepository.mappings["05901234123457"])
    }
}
