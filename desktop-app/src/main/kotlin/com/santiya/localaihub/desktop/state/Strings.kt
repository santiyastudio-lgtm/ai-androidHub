package com.santiya.localaihub.desktop.state

import java.util.Locale

data class DesktopStrings(
    val productName: String,
    val guideTitle: String,
    val guideBody: String,
    val continueLabel: String,
    val termsTitle: String,
    val termsSubtitle: String,
    val scrollToEndLabel: String,
    val acceptLabel: String,
    val setupTitle: String,
    val setupBody: String,
    val storeTitle: String,
    val homeTitle: String,
    val liveTitle: String,
    val filesTitle: String,
    val settingsTitle: String,
    val disconnectedTitle: String
)

fun resolveLocale(selection: AppLocale): Locale {
    return when (selection) {
        AppLocale.RU -> Locale("ru")
        AppLocale.EN -> Locale.ENGLISH
        AppLocale.SYSTEM -> Locale.getDefault()
    }
}

fun stringsFor(locale: Locale): DesktopStrings {
    return if (locale.language.equals("ru", ignoreCase = true)) {
        DesktopStrings(
            productName = "Santiya Local AI Hub",
            guideTitle = "Локальный AI-хаб без облака",
            guideBody = "Windows-клиент повторяет Android-first сценарий: onboarding, setup, store, live AI, files и orchestration остаются внутри одной продуктовой оболочки.",
            continueLabel = "Продолжить",
            termsTitle = "Условия использования",
            termsSubtitle = "Вы управляете моделями, LAN-узлами, файлами и внешним доступом. Критические решения проверяйте отдельно.",
            scrollToEndLabel = "Прокрутите до конца",
            acceptLabel = "Принимаю",
            setupTitle = "Первый запуск",
            setupBody = "Выберите стартовый сценарий, затем настройте рекомендованную модель и projector asset для Live AI.",
            storeTitle = "Магазин моделей",
            homeTitle = "Главная",
            liveTitle = "Live AI",
            filesTitle = "Файлы",
            settingsTitle = "Настройки",
            disconnectedTitle = "Локальный backend пока недоступен"
        )
    } else {
        DesktopStrings(
            productName = "Santiya Local AI Hub",
            guideTitle = "Local AI hub without the cloud",
            guideBody = "The Windows client follows the Android-first product shell: onboarding, setup, store, live AI, files, and orchestration stay in one control flow.",
            continueLabel = "Continue",
            termsTitle = "Terms of use",
            termsSubtitle = "You control models, LAN nodes, files, and external access. Verify critical decisions independently.",
            scrollToEndLabel = "Scroll to the end",
            acceptLabel = "Accept",
            setupTitle = "First launch",
            setupBody = "Pick a startup scenario, then configure the recommended model and projector asset for Live AI.",
            storeTitle = "Model Store",
            homeTitle = "Home",
            liveTitle = "Live AI",
            filesTitle = "Files",
            settingsTitle = "Settings",
            disconnectedTitle = "Backend is not available yet"
        )
    }
}
