package com.pillpronto.domain.usecase

import com.pillpronto.domain.repository.DoseRepository
import javax.inject.Inject
import java.time.LocalDateTime

/** Marcheaza ca MISSED dozele PENDING depasite (cu o perioada de gratie). */
class MarkOverdueDosesUseCase @Inject constructor(
    private val doseRepository: DoseRepository
) {
    suspend operator fun invoke(graceMinutes: Long = 60) =
        doseRepository.markOverdueAsMissed(LocalDateTime.now(), graceMinutes)
}
