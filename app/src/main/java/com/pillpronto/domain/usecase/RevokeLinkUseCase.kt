package com.pillpronto.domain.usecase

import com.pillpronto.domain.repository.LinkRepository
import javax.inject.Inject

class RevokeLinkUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(linkId: String) = linkRepository.revokeLink(linkId)
}
