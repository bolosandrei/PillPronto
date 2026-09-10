package com.pillpronto.domain.usecase

import com.pillpronto.data.sync.GtinCatalogRemoteDataSource
import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.repository.AuthRepository
import com.pillpronto.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val SESSION_SNAPSHOT_TIMEOUT_MS = 5_000L

/** Confirma o mapare GTIN->Cod CIM local (ConfirmGtinMappingUseCase, neschimbat) si, DOAR daca
 * userul curent e autentificat si marcat `isTrustedContributor` (vezi Profile.kt), o propaga si in
 * catalogul partajat `gtin_mappings` (Supabase) — best-effort: un esec de retea NU anuleaza
 * confirmarea locala, la fel ca restul fluxului de scanare (Gs1Parser/ScanBarcode — esecul e
 * tacut). Pt. un user obisnuit (neautentificat sau fara flag), comportamentul ramane identic cu
 * ConfirmGtinMappingUseCase simplu — doar local. */
class ContributeGtinMappingUseCase @Inject constructor(
    private val confirmGtinMapping: ConfirmGtinMappingUseCase,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val remoteDataSource: GtinCatalogRemoteDataSource
) {
    /** true daca maparea a fost propagata si in catalogul comun (util pt. feedback in UI). */
    suspend operator fun invoke(gtin: String, codCim: String): Boolean {
        confirmGtinMapping(gtin, codCim)

        val session = withTimeoutOrNull(SESSION_SNAPSHOT_TIMEOUT_MS) {
            authRepository.sessionStatus.first { it !is AuthSessionState.Loading }
        }
        val userId = (session as? AuthSessionState.Authenticated)?.userId ?: return false
        val isTrustedContributor = profileRepository.getProfile(userId)?.isTrustedContributor ?: false
        if (!isTrustedContributor) return false

        return remoteDataSource.contribute(gtin, codCim).isSuccess
    }
}
