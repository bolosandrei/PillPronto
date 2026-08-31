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
import com.pillpronto.R
import com.pillpronto.domain.model.Treatment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Programeaza remindere ca alarme EXACTE (AlarmManager). Alarma e doar un "nudge";
 * sursa de adevar pentru aderenta ramane DoseLog din baza de date.
 * Nota: pe Android 12+ e nevoie de permisiunea SCHEDULE_EXACT_ALARM (verificata mai jos).
 * Battery optimization pe Xiaomi/Huawei/Samsung se trateaza in Faza 7.
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

    /** Programeaza urmatoarea aparitie pentru fiecare ora din tratament. */
    fun scheduleTreatment(treatment: Treatment) {
        treatment.times.forEachIndexed { index, time ->
            val next = nextOccurrence(time.hour, time.minute)
            schedule(
                requestCode = requestCode(treatment.id, index),
                triggerAtMillis = next,
                medicationName = treatment.medicationName,
                dosage = treatment.dosage,
                hour = time.hour,
                minute = time.minute
            )
        }
    }

    fun cancelTreatment(treatment: Treatment) {
        treatment.times.indices.forEach { index ->
            val pi = buildPendingIntent(requestCode(treatment.id, index), null, null, 0, 0)
            alarmManager.cancel(pi)
        }
    }

    @SuppressLint("MissingPermission")
    fun schedule(
        requestCode: Int,
        triggerAtMillis: Long,
        medicationName: String,
        dosage: String,
        hour: Int,
        minute: Int
    ) {
        val pi = buildPendingIntent(requestCode, medicationName, dosage, hour, minute)
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    fun showNotification(medicationName: String, dosage: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("E timpul pentru $medicationName")
            .setContentText("Doza: $dosage. Atinge aplicatia pentru a confirma.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(medicationName.hashCode(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS neacordata; se solicita din UI.
        }
    }

    private fun buildPendingIntent(
        requestCode: Int, medicationName: String?, dosage: String?, hour: Int, minute: Int
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_MED_NAME, medicationName)
            putExtra(EXTRA_DOSAGE, dosage)
            putExtra(EXTRA_HOUR, hour)
            putExtra(EXTRA_MINUTE, minute)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextOccurrence(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun requestCode(treatmentId: Long, index: Int): Int = (treatmentId * 100 + index).toInt()

    companion object {
        const val CHANNEL_ID = "medication_reminders"
        const val EXTRA_MED_NAME = "extra_med_name"
        const val EXTRA_DOSAGE = "extra_dosage"
        const val EXTRA_HOUR = "extra_hour"
        const val EXTRA_MINUTE = "extra_minute"
    }
}
