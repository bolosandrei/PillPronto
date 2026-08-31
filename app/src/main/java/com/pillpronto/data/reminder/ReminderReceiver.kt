package com.pillpronto.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Primeste alarma pentru o doza si afiseaza notificarea (cu actiuni). */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getLongExtra(ReminderScheduler.EXTRA_DOSE_ID, -1L)
        if (doseId < 0) return
        val medName = intent.getStringExtra(ReminderScheduler.EXTRA_MED_NAME) ?: return
        val dosage = intent.getStringExtra(ReminderScheduler.EXTRA_DOSAGE) ?: ""
        scheduler.showDoseNotification(doseId, medName, dosage)
    }
}
