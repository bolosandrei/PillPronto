package com.pillpronto

import android.app.Application
import com.pillpronto.data.reminder.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PillProntoApp : Application() {

    @Inject lateinit var reminderScheduler: ReminderScheduler

    override fun onCreate() {
        super.onCreate()
        reminderScheduler.createNotificationChannel()
    }
}
