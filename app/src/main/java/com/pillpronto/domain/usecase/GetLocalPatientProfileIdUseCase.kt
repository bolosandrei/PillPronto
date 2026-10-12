package com.pillpronto.domain.usecase

import com.pillpronto.domain.repository.PatientProfileIdProvider
import javax.inject.Inject

/** Identitatea locala a profilului de pacient ACTIV al acestui device (Faza 1.5d — folosita de
 * ManageAccessViewModel ca sa genereze invitatii pentru propriul profil). `currentProfileId()`,
 * NU `patientProfileId.value` — asteapta rezolvarea completa dupa un login proaspat (vezi
 * PatientProfileIdProvider). */
class GetLocalPatientProfileIdUseCase @Inject constructor(
    private val provider: PatientProfileIdProvider
) {
    suspend operator fun invoke(): String = provider.currentProfileId()
}
