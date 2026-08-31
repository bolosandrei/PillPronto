package com.pillpronto.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.usecase.LogDoseUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Trateaza butoanele "Confirma"/"Omite" din notificare, fara a deschide app-ul. */
@AndroidEntryPoint
class DoseActionReceiver : BroadcastReceiver() {

    @Inject lateinit var logDose: LogDoseUseCase
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getLongExtra(ReminderScheduler.EXTRA_DOSE_ID, -1L)
        if (doseId < 0) return
        val status = when (intent.action) {
            ReminderScheduler.ACTION_TAKE -> DoseStatus.TAKEN
            ReminderScheduler.ACTION_SKIP -> DoseStatus.SKIPPED
            else -> return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                logDose(doseId, status)
                scheduler.cancelNotification(doseId)
            } finally {
                pending.finish()
            }
        }
    }
}
