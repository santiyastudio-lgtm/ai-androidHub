package com.santiya.localaihub.global

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.nio.charset.Charset

enum class AppLanguage(val languageTag: String) {
    RUSSIAN("ru"),
    ENGLISH("en")
}

object AppLanguageManager {
    private const val PREFS = "app_language_prefs"
    private const val KEY = "app_language"

    fun readPersistedLanguage(context: Context): AppLanguage {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return AppLanguage.entries.firstOrNull { it.languageTag == stored } ?: AppLanguage.RUSSIAN
    }

    fun persistLanguage(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, language.languageTag)
            .apply()
    }
}

fun localizedText(language: AppLanguage, ru: String, en: String): String {
    return if (language == AppLanguage.RUSSIAN) repairRussianMojibakeIfNeeded(ru) else en
}

fun cleanUiText(text: String): String = repairRussianMojibakeIfNeeded(text)

fun localizedText(context: Context, ru: String, en: String): String {
    return localizedText(AppLanguageManager.readPersistedLanguage(context), ru, en)
}

@Composable
fun localizedText(ru: String, en: String): String {
    return localizedText(LocalContext.current, ru, en)
}

private fun repairRussianMojibakeIfNeeded(text: String): String {
    if (!looksLikeRussianMojibake(text)) {
        return text
    }

    val candidates = linkedSetOf<String>()
    candidates += text

    fun decodeCandidate(value: String, source: Charset) {
        runCatching {
            String(value.toByteArray(source), Charsets.UTF_8)
        }.getOrNull()?.let(candidates::add)
    }

    decodeCandidate(text, Charset.forName("windows-1251"))
    decodeCandidate(text, Charsets.ISO_8859_1)

    repeat(2) {
        candidates.toList().forEach { candidate ->
            decodeCandidate(candidate, Charset.forName("windows-1251"))
            decodeCandidate(candidate, Charsets.ISO_8859_1)
        }
    }

    return candidates
        .map { it.trim() }
        .maxByOrNull(::russianTextScore)
        ?.replace("вЂ”", "—")
        ?.replace("вЂў", "•")
        ?.replace("В«", "«")
        ?.replace("В»", "»")
        ?: text
}

private fun looksLikeRussianMojibake(text: String): Boolean {
    val markers = listOf("Р", "С", "вЂ", "в„ў", "Ð", "Ñ")
    return markers.any { marker -> text.contains(marker) }
}

private fun russianTextScore(text: String): Int {
    val cyrillicCount = text.count { it in '\u0400'..'\u04FF' }
    val mojibakePenalty = listOf("Р", "С", "вЂ", "в„ў", "Ð", "Ñ").sumOf { marker ->
        Regex(Regex.escape(marker)).findAll(text).count()
    }
    return (cyrillicCount * 4) - (mojibakePenalty * 3)
}
