package com.pillpronto.data.local

/**
 * Seam minim peste `LocalPatientProfileProvider`, doar pentru testabilitate — constructorul lui
 * LocalPatientProfileProvider atinge SharedPreferences (Context real), deci nu e fake-uibil direct
 * in teste JVM (acelasi motiv ca ReminderSync/SyncRemoteDataSource, vezi data/sync/SyncManager.kt).
 */
interface PatientProfileIdProvider {
    val patientProfileId: String
}
