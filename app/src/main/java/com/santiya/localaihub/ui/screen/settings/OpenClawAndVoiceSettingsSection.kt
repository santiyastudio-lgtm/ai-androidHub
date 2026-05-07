package com.santiya.localaihub.ui.screen.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.santiya.localaihub.data.AppLanguageSettings
import com.santiya.localaihub.global.AppLanguage
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.global.localizedText
import com.santiya.localaihub.hub.LocalBackendOption
import com.santiya.localaihub.hub.OpenClawCatalog
import com.santiya.localaihub.hub.OpenClawLocalSettings
import com.santiya.localaihub.models.enums.ProviderType
import com.santiya.localaihub.models.table_schema.Model
import com.santiya.localaihub.tts.VoiceRuntimeOption
import com.santiya.localaihub.tts.VoiceRuntimeSettings
import com.santiya.localaihub.ui.components.SectionDivider
import com.santiya.localaihub.ui.components.SectionHeader
import com.santiya.localaihub.ui.components.StandardCard
import com.santiya.localaihub.ui.components.SwitchRow

internal fun LazyListScope.languageSection(
    settings: AppLanguageSettings,
    onSelect: (AppLanguage) -> Unit,
) {
    item { SectionDivider() }
    item { SectionHeader(title = localizedText(settings.language, "Язык интерфейса", "App language")) }
    item {
        StandardCard(
            title = localizedText(settings.language, "Язык интерфейса", "App language"),
            description = localizedText(
                settings.language,
                "Первый экран, главная, магазин и основные статусы переключаются между русским и английским.",
                "The first screen, Home, Store, and core statuses switch between Russian and English."
            ),
        ) {
            ChoiceRow(
                items = listOf(AppLanguage.RUSSIAN, AppLanguage.ENGLISH),
                isSelected = { it == settings.language },
                label = {
                    when (it) {
                        AppLanguage.RUSSIAN -> "Русский"
                        AppLanguage.ENGLISH -> "English"
                    }
                },
                onSelect = onSelect,
            )
        }
    }
}

internal fun LazyListScope.openClawLocalSection(
    language: AppLanguage,
    settings: OpenClawLocalSettings,
    installedModels: List<Model>,
    onBackendSelected: (LocalBackendOption) -> Unit,
    onEnabledByDefaultChange: (Boolean) -> Unit,
    onPreferredModelSelected: (String?) -> Unit,
    onSkillToggle: (String) -> Unit,
    onApiToolToggle: (String) -> Unit,
    onAutoUseRecommendedChange: (Boolean) -> Unit,
    onPreferProjectorChange: (Boolean) -> Unit,
    onShowAdvancedBackendsChange: (Boolean) -> Unit,
) {
    val availableBackends = buildList {
        add(LocalBackendOption.GGUF_LOCAL)
        add(LocalBackendOption.GOOGLE_LOCAL)
        if (settings.showAdvancedBackends) add(LocalBackendOption.ORCHESTRA_LAN)
    }
    val modelOptions = listOf<Model?>(null) + installedModels.filter(::isOpenClawCompatible)
    val skillOptions = listOf(
        "travel_offline" to localizedText(language, "Офлайн-путешествия", "Offline travel"),
        "browser" to localizedText(language, "Браузер", "Browser"),
        "files" to localizedText(language, "Файлы", "Files"),
        "memory" to localizedText(language, "Память", "Memory"),
        "vision" to localizedText(language, "Зрение", "Vision"),
    )
    val apiToolOptions = listOf(
        "web_search" to localizedText(language, "Поиск в интернете", "Web search"),
        "browser" to localizedText(language, "Встроенный браузер", "Embedded browser"),
        "api_models" to localizedText(language, "API-модели", "API models"),
        "support_logs" to localizedText(language, "Логи поддержки", "Support logs"),
    )

    item { SectionDivider() }
    item { SectionHeader(title = localizedText(language, "OpenClaw локально", "OpenClaw Local")) }
    item {
        StandardCard(
            title = localizedText(language, "OpenClaw локально", "OpenClaw Local"),
            description = localizedText(
                language,
                "Локальный агент по мотивам официального OpenClaw: сессии, навыки, инструменты и оркестрация, но без мессенджеров и без терминального onboarding.",
                "A local agent inspired by official OpenClaw: sessions, skills, tools, and orchestration, but without messengers or terminal onboarding."
            ),
        ) {
            SwitchRow(
                title = localizedText(language, "Использовать OpenClaw по умолчанию", "Use OpenClaw by default"),
                description = localizedText(
                    language,
                    "Обычные отправки из чата будут идти через OpenClaw локально, если переключатель включён.",
                    "Ordinary chat sends route through OpenClaw Local when this is enabled."
                ),
                checked = settings.enabledByDefault,
                onCheckedChange = onEnabledByDefaultChange,
            )
        }
    }
    item {
        StandardCard(
            title = localizedText(language, "Бэкенд OpenClaw", "OpenClaw backend"),
            description = localizedText(
                language,
                "GGUF локально — основной путь. Google Local — отдельный локальный рантайм. Оркестр + LAN — расширенный режим.",
                "GGUF Local is the primary path. Google Local is a separate local runtime. Orchestra + LAN is the advanced mode."
            ),
        ) {
            ChoiceRow(
                items = availableBackends,
                isSelected = { it == settings.defaultBackend },
                label = {
                    when (it) {
                        LocalBackendOption.GGUF_LOCAL -> localizedText(language, "GGUF локально", "GGUF Local")
                        LocalBackendOption.GOOGLE_LOCAL -> "Google Local"
                        LocalBackendOption.ORCHESTRA_LAN -> localizedText(language, "Оркестр + LAN", "Orchestra + LAN")
                    }
                },
                onSelect = onBackendSelected,
            )
        }
    }
    item {
        StandardCard(
            title = localizedText(language, "Модель OpenClaw по умолчанию", "Default OpenClaw model"),
            description = localizedText(
                language,
                "OpenClaw использует свой отдельный выбор модели по умолчанию и не обязан совпадать с обычным чатом.",
                "OpenClaw keeps its own default model selector and does not have to match ordinary chat."
            ),
        ) {
            ChoiceRow(
                items = modelOptions,
                isSelected = { option ->
                    option?.id == settings.preferredOpenClawModelId ||
                        (option == null && settings.preferredOpenClawModelId == null)
                },
                label = { option -> option?.modelName ?: localizedText(language, "Не выбрана", "None") },
                onSelect = { option -> onPreferredModelSelected(option?.id) },
            )
        }
    }
    item {
        StandardCard(
            title = localizedText(language, "Выбрать навыки", "Choose skills"),
            description = localizedText(
                language,
                "Какие локальные навыки OpenClaw должен приоритизировать в агентной сессии.",
                "Choose which local skills OpenClaw should prioritize in the agent session."
            ),
        ) {
            ChoiceRow(
                items = skillOptions,
                isSelected = { it.first in settings.selectedSkillIds },
                label = { it.second },
                onSelect = { onSkillToggle(it.first) },
            )
        }
    }
    item {
        StandardCard(
            title = localizedText(language, "Добавить API-инструменты", "Add API tools"),
            description = localizedText(
                language,
                "Набор инструментов, которые OpenClaw может использовать поверх локальной LLM.",
                "Choose which tools OpenClaw can use on top of the local LLM."
            ),
        ) {
            ChoiceRow(
                items = apiToolOptions,
                isSelected = { it.first in settings.selectedApiToolIds },
                label = { it.second },
                onSelect = { onApiToolToggle(it.first) },
            )
        }
    }
    item {
        StandardCard(
            title = localizedText(language, "Рекомендуемая модель", "Recommended model"),
            description = OpenClawCatalog.RECOMMENDED_REPO,
        ) {
            Text(OpenClawCatalog.RECOMMENDED_MODEL_FILE, style = MaterialTheme.typography.bodySmall)
            Text(
                localizedText(
                    language,
                    "Опциональный проектор: ${OpenClawCatalog.RECOMMENDED_PROJECTOR_FILE}",
                    "Optional projector: ${OpenClawCatalog.RECOMMENDED_PROJECTOR_FILE}"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    item {
        SwitchRow(
            title = localizedText(language, "Автоматически ставить рекомендованную Gemma", "Auto-use the recommended Gemma"),
            description = localizedText(
                language,
                "После установки сделать её обычной моделью чата по умолчанию.",
                "After install, make it the ordinary chat default."
            ),
            checked = settings.autoUseRecommendedModel,
            onCheckedChange = onAutoUseRecommendedChange,
        )
    }
    item {
        SwitchRow(
            title = localizedText(language, "Предпочитать mmproj для live", "Prefer mmproj for live"),
            description = localizedText(
                language,
                "Использовать проектор для multimodal и live-сценариев.",
                "Use the projector for multimodal and live scenarios."
            ),
            checked = settings.preferMultimodalProjector,
            onCheckedChange = onPreferProjectorChange,
        )
    }
    item {
        SwitchRow(
            title = localizedText(language, "Показывать расширенные бэкенды", "Show advanced backends"),
            description = localizedText(
                language,
                "По умолчанию OpenClaw работает как локальный GGUF-чат. Расширенные бэкенды показываются только по явному желанию.",
                "By default, OpenClaw runs as a local GGUF chat. Advanced backends are shown only when explicitly enabled."
            ),
            checked = settings.showAdvancedBackends,
            onCheckedChange = onShowAdvancedBackendsChange,
        )
    }
}

internal fun LazyListScope.voiceRuntimeSection(
    language: AppLanguage,
    settings: VoiceRuntimeSettings,
    onRuntimeSelected: (VoiceRuntimeOption) -> Unit,
    onFallbackToLocalChange: (Boolean) -> Unit,
    onPreferClonedVoiceChange: (Boolean) -> Unit,
    onGptSovitsUrlChange: (String) -> Unit,
    onXttsUrlChange: (String) -> Unit,
) {
    item { SectionDivider() }
    item { SectionHeader(title = localizedText(language, "Голос и озвучка в live", "Voice and live TTS")) }
    item {
        StandardCard(
            title = localizedText(language, "Бэкенд озвучки", "Voice backend"),
            description = localizedText(
                language,
                "Silero и Piper как локальный путь, GPT-SoVITS и XTTS/AllTalk как LAN-узлы.",
                "Silero and Piper as local paths, GPT-SoVITS and XTTS/AllTalk as LAN nodes."
            ),
        ) {
            ChoiceRow(
                items = VoiceRuntimeOption.entries.toList(),
                isSelected = { it == settings.selectedRuntime },
                label = {
                    when (it) {
                        VoiceRuntimeOption.SILERO_LOCAL -> localizedText(language, "Silero локально", "Silero Local")
                        VoiceRuntimeOption.PIPER_LOCAL -> localizedText(language, "Piper локально", "Piper Local")
                        VoiceRuntimeOption.GPT_SOVITS_NODE -> localizedText(language, "GPT-SoVITS через LAN", "GPT-SoVITS LAN")
                        VoiceRuntimeOption.XTTS_ALLTALK_NODE -> localizedText(language, "XTTS / AllTalk через LAN", "XTTS / AllTalk LAN")
                    }
                },
                onSelect = onRuntimeSelected,
            )
        }
    }
    item {
        SwitchRow(
            title = localizedText(language, "Резервно переходить на локальный TTS", "Fallback to local TTS"),
            checked = settings.fallbackToLocal,
            onCheckedChange = onFallbackToLocalChange,
        )
    }
    item {
        SwitchRow(
            title = localizedText(language, "Предпочитать клонированный голос", "Prefer cloned voice"),
            checked = settings.preferClonedVoice,
            onCheckedChange = onPreferClonedVoiceChange,
        )
    }
    if (settings.selectedRuntime == VoiceRuntimeOption.GPT_SOVITS_NODE ||
        settings.selectedRuntime == VoiceRuntimeOption.XTTS_ALLTALK_NODE
    ) {
        item {
            StandardCard(
                title = localizedText(language, "LAN-узлы озвучки", "LAN voice nodes"),
                description = localizedText(
                    language,
                    "Эти бэкенды работают как внешние LAN-сервисы, а не как встроенный Android runtime.",
                    "These backends run as external LAN services, not as an embedded Android runtime."
                ),
            ) {
                OutlinedTextField(
                    value = settings.gptSovitsNode.url,
                    onValueChange = onGptSovitsUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("GPT-SoVITS URL") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = settings.xttsAllTalkNode.url,
                    onValueChange = onXttsUrlChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Standards.SpacingSm),
                    label = { Text("XTTS / AllTalk URL") },
                    singleLine = true,
                )
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    items: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            Surface(
                onClick = { onSelect(item) },
                shape = RoundedCornerShape(999.dp),
                color = if (isSelected(item)) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ) {
                Text(
                    text = label(item),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected(item)) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

private fun isOpenClawCompatible(model: Model): Boolean {
    if (model.providerType != ProviderType.GGUF) return false
    val name = model.modelName.lowercase()
    if ("mmproj" in name) return false
    if ("vision" in name && "chat" !in name && "gemma" !in name) return false
    return true
}
