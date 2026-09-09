package com.pillpronto.util

import com.pillpronto.data.local.PatientProfileIdProvider

class FakePatientProfileIdProvider(override val patientProfileId: String = "test-patient") :
    PatientProfileIdProvider
