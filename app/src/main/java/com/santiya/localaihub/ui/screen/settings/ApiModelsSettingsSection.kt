package com.santiya.localaihub.ui.screen.settings

import androidx.compose.foundation.lazy.LazyListScope
import com.santiya.localaihub.global.AppLanguage
import com.santiya.localaihub.global.localizedText
import com.santiya.localaihub.ui.components.SectionDivider
import com.santiya.localaihub.ui.components.SectionHeader
import com.santiya.localaihub.ui.components.StandardCard

internal fun LazyListScope.apiModelsSection(
    language: AppLanguage,
    onOpenApiModels: () -> Unit,
) {
    item { SectionDivider() }
    item { SectionHeader(title = localizedText(language, "API-модели", "API Models")) }
    item {
        StandardCard(
            title = localizedText(language, "Облачные модели через API", "Cloud models over API"),
            description = localizedText(
                language,
                "OpenAI, OpenRouter, DeepSeek, Claude и Gemini. Ключ вводится прямо на экране API-моделей.",
                "OpenAI, OpenRouter, DeepSeek, Claude, and Gemini. Enter the key directly on the API Models screen."
            ),
            onClick = onOpenApiModels
        )
    }
}
