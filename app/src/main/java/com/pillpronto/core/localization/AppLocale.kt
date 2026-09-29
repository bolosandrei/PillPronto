package com.pillpronto.core.localization

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Selector de limbă in-app (RO/EN), independent de tema/temele AppCompat — proiectul e Compose
 * pur, MainActivity extinde ComponentActivity, nu AppCompatActivity, deci nu putem folosi
 * AppCompatDelegate.setApplicationLocales fără riscul unei teme AppCompat cerute în plus.
 *
 * Strategie pe 2 căi, in funcție de API level:
 * - **API 33+ (Android 13+)**: folosește API-ul nativ de platformă `LocaleManager` — e sursa de
 *   adevăr, se sincronizează automat cu ecranul de sistem Setări > Aplicații > PillPronto >
 *   Limbă aplicație (funcțional pentru că `AndroidManifest.xml` declară deja
 *   `android:localeConfig="@xml/locales_config"`), și sistemul recreează automat activitatea
 *   curentă la schimbare — nu mai ținem noi nicio preferință locală.
 * - **API 26-32**: nu există per-app language la nivel de platformă — reținem preferința noi
 *   (SharedPreferences) și suprascriem `Configuration`-ul prin `attachBaseContext` (Application +
 *   Activity), apoi cerem explicit `recreate()` din UI ca să se aplice imediat.
 */
object AppLocale {

    /** null = urmează limba sistemului (fără suprascriere). */
    const val LANGUAGE_ROMANIAN = "ro"
    const val LANGUAGE_ENGLISH = "en"

    private const val PREFS_NAME = "app_locale_prefs"
    private const val KEY_LANGUAGE = "language"

    /** Limba selectată explicit de user, sau null dacă urmează limba sistemului. */
    fun getLanguage(context: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            return locales?.takeIf { !it.isEmpty }?.get(0)?.language
        }
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
    }

    /**
     * Setează limba aplicației. `language` = [LANGUAGE_ROMANIAN]/[LANGUAGE_ENGLISH] sau null
     * pentru "urmează sistemul". Pe API 26-32, apelantul (UI) trebuie să recreeze activitatea
     * după apel ca schimbarea să se vadă imediat — pe 33+ sistemul o face automat.
     */
    fun setLanguage(context: Context, language: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            localeManager?.applicationLocales =
                if (language == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language)
            return
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            if (language == null) remove(KEY_LANGUAGE) else putString(KEY_LANGUAGE, language)
        }.apply()
    }

    /**
     * Suprascrie `Configuration`-ul contextului de bază cu limba salvată — de apelat din
     * `attachBaseContext` (Application + Activity). No-op pe API 33+ (sistemul aplică deja
     * configurația corectă la toate contextele, nu mai trebuie dublat manual).
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val language = getLanguage(base) ?: return base
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = android.content.res.Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
