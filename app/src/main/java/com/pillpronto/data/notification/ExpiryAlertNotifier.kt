package com.pillpronto.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.content.Context
import com.pillpronto.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private val DMY = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** Notificare locala de expirare apropiata/depasita a unui tratament (extensie Faza 2b-i). Clasa
 * separata de `ReminderScheduler`/`CaregiverAlertNotifier` — informativa, fara actiuni, deschide
 * app-ul la tap. Vezi `ExpiryAlertChecker` pt. logica de prag/deduplicare. */
@Singleton
class ExpiryAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.expiry_alerts_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = context.getString(R.string.expiry_alerts_channel_desc) }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun showNearExpiryAlert(treatmentId: Long, medicationName: String, expiryDate: LocalDate) {
        notify(
            treatmentId,
            context.getString(R.string.expiry_alert_near_title, medicationName),
            context.getString(R.string.expiry_alert_near_text, DMY.format(expiryDate))
        )
    }

    fun showExpiredAlert(treatmentId: Long, medicationName: String) {
        notify(
            treatmentId,
            context.getString(R.string.expiry_alert_expired_title, medicationName),
            context.getString(R.string.expiry_alert_expired_text)
        )
    }

    private fun notify(treatmentId: Long, title: String, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try {
            // notificationId derivat din treatmentId, nu hashCode-ul textului — o alerta noua pt.
            // acelasi tratament (ex. NEAR_EXPIRY apoi EXPIRED) inlocuieste cu naturalete vechea
            // notificare, nu o dubleaza in tray.
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_OFFSET + treatmentId.toInt(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS neacordata; se solicita din UI (MainActivity).
        }
    }

    companion object {
        const val CHANNEL_ID = "expiry_alerts"

        // Offset mare, sa nu se suprapuna cu id-urile de notificare deja folosite in alta parte
        // (ReminderScheduler foloseste doseId.toInt() direct, CaregiverAlertNotifier foloseste
        // displayName.hashCode()).
        private const val NOTIFICATION_ID_OFFSET = 2_000_000
    }
}
