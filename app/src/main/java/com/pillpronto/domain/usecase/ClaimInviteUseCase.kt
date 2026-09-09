package com.pillpronto.domain.usecase

import com.pillpronto.domain.repository.LinkRepository
import javax.inject.Inject

class ClaimInviteUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(code: String): Result<Unit> = linkRepository.claimInvite(code)
}
