package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.PendingClinicianVerification
import com.pillpronto.domain.repository.ProfileRepository
import javax.inject.Inject

/** Cererile de verificare in asteptare, vizibile DOAR unui reviewer desemnat manual — vezi
 * ProfileRepository.listPendingClinicianVerifications. */
class ListPendingClinicianVerificationsUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(): List<PendingClinicianVerification> = profileRepository.listPendingClinicianVerifications()
}
