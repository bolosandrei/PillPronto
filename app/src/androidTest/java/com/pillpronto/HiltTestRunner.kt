package com.pillpronto

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/** Instrumentation runner custom — instantiaza HiltTestApplication in loc de PillProntoApp,
 * necesar pentru orice test @HiltAndroidTest. Configurat in app/build.gradle.kts. */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
