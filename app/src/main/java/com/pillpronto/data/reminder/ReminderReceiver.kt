package com.pillpronto.data.reminder

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

/** Primeste alarma, afiseaza notificarea si reprogrameaza pentru ziua urmatoare. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: ReminderScheduler

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val medName = intent.getStringExtra(ReminderScheduler.EXTRA_MED_NAME) ?: return
        val dosage = intent.getStringExtra(ReminderScheduler.EXTRA_DOSAGE) ?: ""
        val hour = intent.getIntExtra(ReminderScheduler.EXTRA_HOUR, -1)
        val minute = intent.getIntExtra(ReminderScheduler.EXTRA_MINUTE, -1)

        scheduler.showNotification(medName, dosage)

        // Reprogrameaza aceeasi ora pentru maine (fereastra rulanta).
        if (hour in 0..23 && minute in 0..59) {
            val next = LocalDateTime.now()
                .withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                .plusDays(1)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            scheduler.schedule(
                requestCode = (medName.hashCode() and 0xffff),
                triggerAtMillis = next,
                medicationName = medName,
                dosage = dosage,
                hour = hour,
                minute = minute
            )
        }
    }
}
