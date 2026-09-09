package com.pillpronto.domain.usecase

import com.pillpronto.domain.repository.PatientProfileIdProvider
import javax.inject.Inject

/** Identitatea locala a profilului de pacient al acestui device (Faza 1.5d — folosita de
 * ManageAccessViewModel ca sa genereze invitatii pentru propriul profil). */
class GetLocalPatientProfileIdUseCase @Inject constructor(
    private val provider: PatientProfileIdProvider
) {
    operator fun invoke(): String = provider.patientProfileId
}
