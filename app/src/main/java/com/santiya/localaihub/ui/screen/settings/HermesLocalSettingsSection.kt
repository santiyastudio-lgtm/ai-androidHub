package com.santiya.localaihub.ui.screen.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.santiya.localaihub.global.AppLanguage
import com.santiya.localaihub.global.localizedText
import com.santiya.localaihub.hub.OpenClawCatalog
import com.santiya.localaihub.hub.OpenClawLocalSettings
import com.santiya.localaihub.ui.components.SectionDivider
import com.santiya.localaihub.ui.components.SectionHeader
import com.santiya.localaihub.ui.components.StandardCard
import com.santiya.localaihub.ui.components.SwitchRow

/** User-facing local agent settings. Legacy OpenClaw-named storage stays internal for migration. */
internal fun LazyListScope.hermesLocalSection(
    language: AppLanguage,
    settings: OpenClawLocalSettings,
    onEnabledByDefaultChange: (Boolean) -> Unit,
    onAutoUseRecommendedChange: (Boolean) -> Unit,
) {
    item { SectionDivider() }
    item { SectionHeader(title = localizedText(language, "Локальный агент Hermes", "Hermes Local Agent")) }
    item {
        StandardCard(
            title = localizedText(language, "Hermes Local", "Hermes Local"),
            description = localizedText(
                language,
                "Агент работает на модели GGUF прямо на устройстве. Инструменты Hermes выполняются в песочнице приложения; внешний Gateway и облачный агент не используются.",
                "The agent runs a GGUF model on-device. Hermes tools execute inside the app sandbox; no external gateway or cloud agent is used.",
            ),
        ) {
            SwitchRow(
                title = localizedText(language, "Включать Hermes по умолчанию", "Use Hermes by default"),
                description = localizedText(
                    language,
                    "Обычные сообщения чата будут использовать локальный агент и доступные инструменты.",
                    "Regular chat messages use the local agent and its available tools.",
                ),
                checked = settings.enabledByDefault,
                onCheckedChange = onEnabledByDefaultChange,
            )
        }
    }
    item {
        StandardCard(
            title = localizedText(language, "Рекомендуемая модель", "Recommended model"),
            description = OpenClawCatalog.RECOMMENDED_REPO,
        ) {
            Text(
                OpenClawCatalog.RECOMMENDED_MODEL_FILE,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                localizedText(
                    language,
                    "Квантование Q4_K_M снижает требования к памяти. Для работы импортируйте GGUF-файл в хаб и выберите его как модель чата.",
                    "The Q4_K_M quantization lowers memory requirements. Import the GGUF file into the hub and select it as the chat model.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchRow(
                title = localizedText(language, "Автоматически выбрать Hermes", "Auto-select Hermes"),
                description = localizedText(
                    language,
                    "После импорта рекомендованная модель станет моделью чата по умолчанию.",
                    "After import, the recommended model becomes the default chat model.",
                ),
                checked = settings.autoUseRecommendedModel,
                onCheckedChange = onAutoUseRecommendedChange,
            )
        }
    }
    item {
        StandardCard(
            title = localizedText(language, "Инструменты Hermes", "Hermes tools"),
            description = localizedText(
                language,
                "Доступны браузер внутри приложения, файлы рабочей папки, заметки, сценарии в песочнице и статус геолокации. Операции с файлами ограничены рабочей папкой приложения.",
                "Available: in-app browser, workspace files, notes, sandboxed scripts, and location status. File operations are limited to the app workspace.",
            ),
        )
    }
}
