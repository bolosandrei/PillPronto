package com.pillpronto.util

import com.pillpronto.domain.repository.PatientProfileIdProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Fake simplu, fara nicio logica de rezolvare (vezi ActivePatientProfileCoordinator pt. aceea,
 * testat separat) — teste care au nevoie sa simuleze o comutare de cont apeleaza `set(id)`. */
class FakePatientProfileIdProvider(initial: String = "test-patient") : PatientProfileIdProvider {
    private val _patientProfileId = MutableStateFlow(initial)
    override val patientProfileId: StateFlow<String> = _patientProfileId.asStateFlow()

    override suspend fun currentProfileId(): String = _patientProfileId.value

    fun set(id: String) {
        _patientProfileId.value = id
    }
}
