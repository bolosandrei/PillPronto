package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.DoseItem
import com.pillpronto.domain.repository.DoseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import java.time.LocalDate

class ObserveTodayDosesUseCase @Inject constructor(
    private val doseRepository: DoseRepository
) {
    operator fun invoke(date: LocalDate = LocalDate.now()): Flow<List<DoseItem>> =
        doseRepository.observeDosesForDate(date)
}
