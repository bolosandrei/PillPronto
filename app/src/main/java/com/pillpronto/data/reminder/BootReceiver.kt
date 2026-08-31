package com.pillpronto.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pillpronto.domain.repository.TreatmentRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Reprogrameaza reminderele dupa repornirea telefonului. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var treatmentRepository: TreatmentRepository
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                treatmentRepository.getActiveTreatments().forEach { scheduler.scheduleTreatment(it) }
            } finally {
                pending.finish()
            }
        }
    }
}
