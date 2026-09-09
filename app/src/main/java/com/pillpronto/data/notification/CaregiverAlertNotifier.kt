package com.pillpronto.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.content.Context
import com.pillpronto.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Notificare locala pentru Apartinator la doza ratata a unui pacient legat (Faza 1.5d). Clasa
 * separata de `ReminderScheduler` — acela e specific remindere proprii cu actiuni
 * Confirma/Omite, aici e doar informativ (deschide app-ul la tap, fara actiuni). */
@Singleton
class CaregiverAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.caregiver_alerts_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = context.getString(R.string.caregiver_alerts_channel_desc) }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun showMissedDoseAlert(patientDisplayName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.caregiver_alert_missed_dose_title, patientDisplayName))
            .setContentText(context.getString(R.string.caregiver_alert_missed_dose_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(patientDisplayName.hashCode(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS neacordata; se solicita din UI (MainActivity).
        }
    }

    companion object {
        const val CHANNEL_ID = "caregiver_alerts"
    }
}
