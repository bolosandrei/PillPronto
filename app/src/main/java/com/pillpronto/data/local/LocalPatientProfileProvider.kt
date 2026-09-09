package com.pillpronto.data.local

import android.content.Context
import androidx.core.content.edit
import com.pillpronto.domain.repository.PatientProfileIdProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Identitatea locala a "profilului de pacient" curent de pe acest device — un UUID generat o
 * singura data la prima pornire si persistat local. Devine `patient_profiles.id` in Supabase
 * cand userul isi creeaza cont (Faza 1.5b+): UUID-ul e generat de client, nu de server, deci
 * legarea local <-> remote se face printr-un INSERT direct, fara nicio reconciliere de date.
 *
 * Pana la introducerea conturilor, e folosit doar ca sa marcheze randurile din Room ca apartinand
 * unui singur profil implicit — nu exista inca notiunea de mai multe profiluri pe un device.
 */
@Singleton
class LocalPatientProfileProvider @Inject constructor(
    @ApplicationContext context: Context
) : PatientProfileIdProvider {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override val patientProfileId: String by lazy {
        prefs.getString(KEY_PATIENT_PROFILE_ID, null) ?: UUID.randomUUID().toString().also { id ->
            prefs.edit { putString(KEY_PATIENT_PROFILE_ID, id) }
        }
    }

    private companion object {
        const val PREFS_NAME = "pillpronto_identity"
        const val KEY_PATIENT_PROFILE_ID = "local_patient_profile_id"
    }
}
