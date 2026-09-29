package com.pillpronto.util

import com.pillpronto.data.reminder.ReminderSync

class FakeReminderSync : ReminderSync {
    var callCount = 0
        private set
    val cancelledDoseIds = mutableListOf<Long>()

    override suspend fun syncReminders(horizonDays: Long) {
        callCount++
    }

    override suspend fun cancelDose(doseId: Long) {
        cancelledDoseIds.add(doseId)
    }
}
