package com.santiya.localaihub.data

import android.content.Context
import com.santiya.localaihub.global.AppLanguage
import com.santiya.localaihub.global.AppLanguageManager

data class AppLanguageSettings(
    val language: AppLanguage = AppLanguage.RUSSIAN,
    val hasChosenLanguage: Boolean = false,
)

class AppLanguageSettingsStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("app_language_prefs", Context.MODE_PRIVATE)

    fun read(): AppLanguageSettings {
        return AppLanguageSettings(
            language = AppLanguageManager.readPersistedLanguage(context),
            hasChosenLanguage = prefs.getBoolean("hasChosenLanguage", false),
        )
    }

    fun write(settings: AppLanguageSettings) {
        AppLanguageManager.persistLanguage(context, settings.language)
        prefs.edit()
            .putBoolean("hasChosenLanguage", settings.hasChosenLanguage)
            .apply()
    }

    fun choose(language: AppLanguage) {
        write(AppLanguageSettings(language = language, hasChosenLanguage = true))
    }
}
