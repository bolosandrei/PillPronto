package com.pillpronto.data.notification

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Persista cheile de deduplicare (`ExpiryAlert.dedupKey`) deja notificate, ca sa nu se renotifice
 * la fiecare ciclu al `ExpiryAlertWorker` — vezi `ExpiryAlertChecker`. */
@Singleton
class NotifiedExpiryAlertsStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getNotifiedKeys(): Set<String> = prefs.getStringSet(KEY_NOTIFIED_KEYS, emptySet()) ?: emptySet()

    fun setNotifiedKeys(keys: Set<String>) = prefs.edit { putStringSet(KEY_NOTIFIED_KEYS, keys) }

    private companion object {
        const val PREFS_NAME = "pillpronto_expiry_alerts"
        const val KEY_NOTIFIED_KEYS = "notified_expiry_dedup_keys"
    }
}
