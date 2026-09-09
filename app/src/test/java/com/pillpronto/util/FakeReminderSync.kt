package com.pillpronto.util

import com.pillpronto.data.reminder.ReminderSync

class FakeReminderSync : ReminderSync {
    var callCount = 0
        private set

    override suspend fun syncReminders(horizonDays: Long) {
        callCount++
    }
}
