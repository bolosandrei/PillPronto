package com.pillpronto.data.local

import android.content.Context
import androidx.core.content.edit
import com.pillpronto.domain.repository.GuestProfileIdProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sursa UUID-ului local original, generat o singura data la prima pornire si persistat (
 * SharedPreferences `pillpronto_identity`) — NEMODIFICATA ca mecanism fata de versiunea initiala
 * (Faza 1.5a). Devine `patient_profiles.id` in Supabase pt. PRIMUL cont care il adopta.
 *
 * De la fix-ul de izolare a datelor locale (vezi CLAUDE.md): acest id e strict bucket-ul "guest"
 * — folosit cand NU exista sesiune autentificata, si oferit o singura data ca opțiune explicita
 * de migrare primului cont care se autentifica pe acest device daca bucket-ul are date neclaimate
 * (vezi `ActivePatientProfileCoordinator`). NU mai e identitatea implicita a oricarui cont — asta
 * era bug-ul: un cont nou adopta tacit acest id si vedea local tratamentele contului anterior.
 */
@Singleton
class LocalPatientProfileProvider @Inject constructor(
    @ApplicationContext context: Context
) : GuestProfileIdProvider {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override val guestProfileId: String by lazy {
        prefs.getString(KEY_PATIENT_PROFILE_ID, null) ?: UUID.randomUUID().toString().also { id ->
            prefs.edit { putString(KEY_PATIENT_PROFILE_ID, id) }
        }
    }

    private companion object {
        const val PREFS_NAME = "pillpronto_identity"
        const val KEY_PATIENT_PROFILE_ID = "local_patient_profile_id"
    }
}
