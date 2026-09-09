package com.pillpronto.util

import com.pillpronto.domain.model.LinkedPatientData
import com.pillpronto.domain.repository.LinkedPatientDataRepository

/** Fake simplu pentru testarea use-case-urilor/ViewModel-urilor care citesc date REMOTE ale unui
 * pacient legat (Faza 1.5d). */
class FakeLinkedPatientDataRepository : LinkedPatientDataRepository {

    val dataByPatient = mutableMapOf<String, LinkedPatientData>()
    var error: Throwable? = null

    override suspend fun getPatientData(patientProfileId: String): LinkedPatientData {
        error?.let { throw it }
        return dataByPatient[patientProfileId] ?: LinkedPatientData(emptyList(), emptyList())
    }
}
