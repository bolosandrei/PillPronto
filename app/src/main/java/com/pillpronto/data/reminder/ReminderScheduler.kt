package com.pillpronto.data.reminder

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pillpronto.MainActivity
import com.pillpronto.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Programeaza remindere ca alarme EXACTE per-doza (carauza: doseId).
 * Notificarea: tap -> deschide app; butoane "Confirma"/"Omite" -> marcheaza doza direct.
 * Sursa de adevar pentru aderenta ramane DoseLog. Battery optimization -> Faza 7.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = context.getString(R.string.reminder_channel_desc) }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true

    @SuppressLint("MissingPermission")
    fun scheduleDose(doseId: Long, triggerAtMillis: Long, medName: String, dosage: String) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_DOSE_ID, doseId)
            putExtra(EXTRA_MED_NAME, medName)
            putExtra(EXTRA_DOSAGE, dosage)
        }
        val pi = PendingIntent.getBroadcast(
            context, doseId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    fun cancelDose(doseId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, doseId.toInt(), intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let { alarmManager.cancel(it) }
        cancelNotification(doseId)
    }

    fun showDoseNotification(doseId: Long, medName: String, dosage: String) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_TODAY, true)
        }
        val contentPi = PendingIntent.getActivity(
            context, doseId.toInt(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val takePi = actionPendingIntent(doseId, ACTION_TAKE, medName, dosage)
        val skipPi = actionPendingIntent(doseId, ACTION_SKIP, medName, dosage)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.reminder_notification_title, medName))
            .setContentText(context.getString(R.string.reminder_notification_text, dosage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .addAction(0, context.getString(R.string.common_confirm_take), takePi)
            .addAction(0, context.getString(R.string.common_skip), skipPi)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(doseId.toInt(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS neacordata; se solicita din UI.
        }
    }

    fun cancelNotification(doseId: Long) =
        NotificationManagerCompat.from(context).cancel(doseId.toInt())

    private fun actionPendingIntent(doseId: Long, action: String, medName: String, dosage: String): PendingIntent {
        val intent = Intent(context, DoseActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_DOSE_ID, doseId)
            putExtra(EXTRA_MED_NAME, medName)
            putExtra(EXTRA_DOSAGE, dosage)
        }
        val code = (doseId.toInt() * 10) + if (action == ACTION_TAKE) 1 else 2
        return PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "medication_reminders"
        const val EXTRA_DOSE_ID = "extra_dose_id"
        const val EXTRA_MED_NAME = "extra_med_name"
        const val EXTRA_DOSAGE = "extra_dosage"
        const val ACTION_TAKE = "com.pillpronto.action.TAKE"
        const val ACTION_SKIP = "com.pillpronto.action.SKIP"

        fun toEpochMillis(dt: LocalDateTime): Long =
            dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
