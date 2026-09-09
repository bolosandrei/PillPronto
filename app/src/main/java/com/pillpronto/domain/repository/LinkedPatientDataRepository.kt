package com.pillpronto.domain.repository

import com.pillpronto.domain.model.LinkedPatientData

/** Citire read-only a tratamentelor+dozelor unui pacient legat, direct din Supabase (Faza 1.5d)
 * — niciodata din Room local, care nu contine datele altui profil de pacient. */
interface LinkedPatientDataRepository {
    suspend fun getPatientData(patientProfileId: String): LinkedPatientData
}
