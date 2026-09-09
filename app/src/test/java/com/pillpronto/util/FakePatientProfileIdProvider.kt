package com.pillpronto.util

import com.pillpronto.domain.repository.PatientProfileIdProvider

class FakePatientProfileIdProvider(override val patientProfileId: String = "test-patient") :
    PatientProfileIdProvider
