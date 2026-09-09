package com.pillpronto.data.notification

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Persista id-urile (remoteId) dozelor MISSED deja notificate Apartinatorului, ca sa nu se
 * renotifice la fiecare ciclu al `CaregiverAlertWorker` (Faza 1.5d). */
@Singleton
class NotifiedMissedDosesStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getNotifiedIds(): Set<String> = prefs.getStringSet(KEY_NOTIFIED_IDS, emptySet()) ?: emptySet()

    fun setNotifiedIds(ids: Set<String>) = prefs.edit { putStringSet(KEY_NOTIFIED_IDS, ids) }

    private companion object {
        const val PREFS_NAME = "pillpronto_caregiver_alerts"
        const val KEY_NOTIFIED_IDS = "notified_missed_dose_ids"
    }
}
