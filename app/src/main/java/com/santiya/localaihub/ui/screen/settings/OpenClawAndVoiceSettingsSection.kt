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
    item { SectionHeader(title = localizedText(settings.language, "РЇР·С‹Рє РёРЅС‚РµСЂС„РµР№СЃР°", "App language")) }
    item {
        StandardCard(
            title = localizedText(settings.language, "РЇР·С‹Рє РёРЅС‚РµСЂС„РµР№СЃР°", "App language"),
            description = localizedText(
                settings.language,
                "РџРµСЂРІС‹Р№ СЌРєСЂР°РЅ, РіР»Р°РІРЅР°СЏ, РјР°РіР°Р·РёРЅ Рё РѕСЃРЅРѕРІРЅС‹Рµ СЃС‚Р°С‚СѓСЃС‹ РїРµСЂРµРєР»СЋС‡Р°СЋС‚СЃСЏ РјРµР¶РґСѓ СЂСѓСЃСЃРєРёРј Рё Р°РЅРіР»РёР№СЃРєРёРј.",
                "The first screen, Home, Store, and core statuses switch between Russian and English."
            ),
        ) {
            ChoiceRow(
                items = listOf(AppLanguage.RUSSIAN, AppLanguage.ENGLISH),
                isSelected = { it == settings.language },
                label = {
                    when (it) {
                        AppLanguage.RUSSIAN -> "Р СѓСЃСЃРєРёР№"
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
    onAirLlmEndpointChange: (String) -> Unit,
    onAirLlmModelIdChange: (String) -> Unit,
    onOfficialGatewayEndpointChange: (String) -> Unit,
    onOfficialGatewayTokenChange: (String) -> Unit,
    onOfficialGatewayModelIdChange: (String) -> Unit,
    onAutoUseRecommendedChange: (Boolean) -> Unit,
    onPreferProjectorChange: (Boolean) -> Unit,
    onShowAdvancedBackendsChange: (Boolean) -> Unit,
) {
    val availableBackends = buildList {
        add(LocalBackendOption.GGUF_LOCAL)
        add(LocalBackendOption.GOOGLE_LOCAL)
        add(LocalBackendOption.AIRLLM_REMOTE)
        add(LocalBackendOption.OFFICIAL_GATEWAY)
        add(LocalBackendOption.TERMUX_LOCAL)
        if (settings.showAdvancedBackends) add(LocalBackendOption.ORCHESTRA_LAN)
    }
    val modelOptions = listOf<Model?>(null) + installedModels.filter(::isOpenClawCompatible)
    val skillOptions = listOf(
        "hermes" to localizedText(language, "Hermes-Р°РіРµРЅС‚", "Hermes agent"),
        "travel_offline" to localizedText(language, "РћС„Р»Р°Р№РЅ-РїСѓС‚РµС€РµСЃС‚РІРёСЏ", "Offline travel"),
        "browser" to localizedText(language, "Р‘СЂР°СѓР·РµСЂ", "Browser"),
        "files" to localizedText(language, "Р¤Р°Р№Р»С‹ Рё РєРѕРґ", "Files and code"),
        "memory" to localizedText(language, "РџР°РјСЏС‚СЊ", "Memory"),
        "automation" to localizedText(language, "РђРІС‚РѕРјР°С‚РёР·Р°С†РёСЏ", "Automation"),
        "termux" to "Termux runtime",
        "airllm" to "AirLLM gateway",
        "official_openclaw_gateway" to "Official OpenClaw Gateway",
        "vision" to localizedText(language, "Р—СЂРµРЅРёРµ", "Vision"),
    )
    val apiToolOptions = listOf(
        "hermes" to "Hermes tools",
        "web_search" to localizedText(language, "РџРѕРёСЃРє РІ РёРЅС‚РµСЂРЅРµС‚Рµ", "Web search"),
        "browser" to localizedText(language, "Р’СЃС‚СЂРѕРµРЅРЅС‹Р№ Р±СЂР°СѓР·РµСЂ", "Embedded browser"),
        "api_models" to localizedText(language, "API-РјРѕРґРµР»Рё", "API models"),
        "support_logs" to localizedText(language, "Р›РѕРіРё РїРѕРґРґРµСЂР¶РєРё", "Support logs"),
        "system_info" to localizedText(language, "РЎРѕСЃС‚РѕСЏРЅРёРµ СЃРёСЃС‚РµРјС‹", "System info"),
        "location_control" to localizedText(language, "РЎС‚Р°С‚СѓСЃ GPS", "GPS status"),
        "termux" to "Termux bridge",
        "airllm" to "AirLLM gateway",
        "official_openclaw_gateway" to "Official OpenClaw Gateway",
    )

    item { SectionDivider() }
    item { SectionHeader(title = localizedText(language, "OpenClaw Р»РѕРєР°Р»СЊРЅРѕ", "OpenClaw Local")) }
    item {
        StandardCard(
            title = localizedText(language, "OpenClaw Р»РѕРєР°Р»СЊРЅРѕ", "OpenClaw Local"),
            description = localizedText(
                language,
                "Р›РѕРєР°Р»СЊРЅС‹Р№ Р°РіРµРЅС‚: СЃРµСЃСЃРёРё, РЅР°РІС‹РєРё, РёРЅСЃС‚СЂСѓРјРµРЅС‚С‹, Hermes-Р°РІС‚РѕРјР°С‚РёР·Р°С†РёСЏ, Р±СЂР°СѓР·РµСЂ Рё РІС‹РїРѕР»РЅРµРЅРёРµ Р·Р°РґР°С‡ Р±РµР· РјРµСЃСЃРµРЅРґР¶РµСЂ-РєР°РЅР°Р»РѕРІ.",
                "Local agent mode: sessions, skills, tools, Hermes automation, browser handoff, and task execution without messenger channels."
            ),
        ) {
            SwitchRow(
                title = localizedText(language, "РСЃРїРѕР»СЊР·РѕРІР°С‚СЊ OpenClaw РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ", "Use OpenClaw by default"),
                description = localizedText(
                    language,
                    "РћР±С‹С‡РЅС‹Рµ РѕС‚РїСЂР°РІРєРё РёР· С‡Р°С‚Р° Р±СѓРґСѓС‚ РёРґС‚Рё С‡РµСЂРµР· OpenClaw Local, РµСЃР»Рё РїРµСЂРµРєР»СЋС‡Р°С‚РµР»СЊ РІРєР»СЋС‡С‘РЅ.",
                    "Ordinary chat sends route through OpenClaw Local when this is enabled."
                ),
                checked = settings.enabledByDefault,
                onCheckedChange = onEnabledByDefaultChange,
            )
        }
    }
    item {
        StandardCard(
            title = localizedText(language, "Р‘СЌРєРµРЅРґ OpenClaw", "OpenClaw backend"),
            description = localizedText(
                language,
                "GGUF и Google Local работают внутри хаба. Termux Local запускает официальный OpenClaw Gateway прямо на устройстве через Termux. AirLLM подключается как локальный/private-LAN Python gateway для больших HF-моделей.",
                "GGUF and Google Local run inside the hub. Termux Local starts the official OpenClaw Gateway on the device through Termux. AirLLM connects as a local/private-LAN Python gateway for large HF models."
            ),
        ) {
            ChoiceRow(
                items = availableBackends,
                isSelected = { it == settings.defaultBackend },
                label = {
                    when (it) {
                        LocalBackendOption.GGUF_LOCAL -> localizedText(language, "GGUF Р»РѕРєР°Р»СЊРЅРѕ", "GGUF Local")
                        LocalBackendOption.GOOGLE_LOCAL -> "Google Local"
                        LocalBackendOption.AIRLLM_REMOTE -> "AirLLM"
                        LocalBackendOption.OFFICIAL_GATEWAY -> "OpenClaw Gateway"
                        LocalBackendOption.TERMUX_LOCAL -> "Termux Local"
                        LocalBackendOption.ORCHESTRA_LAN -> localizedText(language, "РћСЂРєРµСЃС‚СЂ + LAN", "Orchestra + LAN")
                    }
                },
                onSelect = onBackendSelected,
            )
        }
    }
    item {
        StandardCard(
            title = localizedText(language, "AirLLM РґР»СЏ Р±РѕР»СЊС€РёС… HF-РјРѕРґРµР»РµР№", "AirLLM for large HF models"),
            description = localizedText(
                language,
                "AirLLM РЅРµ СЏРІР»СЏРµС‚СЃСЏ Android-Р±РёР±Р»РёРѕС‚РµРєРѕР№. Р—Р°РїСѓСЃС‚РёС‚Рµ Python/PyTorch gateway РІ Termux, proot, РЅР° РџРљ РёР»Рё РІ LAN, Р° С…Р°Р± Р±СѓРґРµС‚ РІС‹Р·С‹РІР°С‚СЊ РµРіРѕ РєР°Рє РёРЅСЃС‚СЂСѓРјРµРЅС‚ OpenClaw.",
                "AirLLM is not an Android library. Run the Python/PyTorch gateway in Termux, proot, desktop, or LAN, and the hub will call it as an OpenClaw tool."
            ),
        ) {
            OutlinedTextField(
                value = settings.airLlmEndpoint,
                onValueChange = onAirLlmEndpointChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Gateway URL") },
                singleLine = true,
            )
            OutlinedTextField(
                value = settings.airLlmModelId,
                onValueChange = onAirLlmModelIdChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Standards.SpacingSm),
                label = { Text(localizedText(language, "HF model id / Р»РѕРєР°Р»СЊРЅС‹Р№ РїСѓС‚СЊ", "HF model id / local path")) },
                placeholder = { Text("Qwen/Qwen2.5-1.5B-Instruct") },
                singleLine = true,
            )
        }
    }
    item {
        StandardCard(
            title = localizedText(language, "Official OpenClaw Gateway", "Official OpenClaw Gateway"),
            description = localizedText(
                language,
                "Подключение к официальному openclaw/openclaw Gateway: Node.js процесс `openclaw gateway --bind loopback --port 18789 --allow-unconfigured`, WebSocket control plane и совместимый HTTP-слой, если он включён. Endpoint ограничен localhost/private LAN.",
                "Connect to the official openclaw/openclaw Gateway: Node.js `openclaw gateway --bind loopback --port 18789 --allow-unconfigured`, WebSocket control plane, and compatible HTTP layer when enabled. Endpoint is limited to localhost/private LAN."
            ),
        ) {
            OutlinedTextField(
                value = settings.officialGatewayEndpoint,
                onValueChange = onOfficialGatewayEndpointChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Gateway URL") },
                placeholder = { Text("http://127.0.0.1:18789") },
                singleLine = true,
            )
            OutlinedTextField(
                value = settings.officialGatewayModelId,
                onValueChange = onOfficialGatewayModelIdChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Standards.SpacingSm),
                label = { Text("Model") },
                placeholder = { Text("openclaw/default") },
                singleLine = true,
            )
            OutlinedTextField(
                value = settings.officialGatewayToken,
                onValueChange = onOfficialGatewayTokenChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Standards.SpacingSm),
                label = { Text("Gateway token") },
                placeholder = { Text(localizedText(language, "Если включена авторизация", "If auth is enabled")) },
                singleLine = true,
            )
        }
    }
    item {
        StandardCard(
            title = localizedText(language, "OpenClaw через Termux", "OpenClaw through Termux"),
            description = localizedText(
                language,
                "Локальный режим для телефона: выберите backend Termux Local, установите Termux, выдайте разрешение RUN_COMMAND и попросите агента: `запусти OpenClaw Gateway в Termux`. Хаб установит официальный CLI, поднимет Gateway на 127.0.0.1:18789 и будет работать через него.",
                "Phone-local mode: choose the Termux Local backend, install Termux, grant RUN_COMMAND permission, then ask the agent: `start OpenClaw Gateway in Termux`. The hub installs the official CLI, starts Gateway on 127.0.0.1:18789, and routes through it."
            ),
        ) {
            Text(
                text = localizedText(
                    language,
                    "Безопасный режим по умолчанию: bind loopback. Для LAN нужен токен и отдельная настройка endpoint.",
                    "Safe default: bind loopback. LAN access requires a token and a separate endpoint setting."
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    item {
        StandardCard(
            title = localizedText(language, "РњРѕРґРµР»СЊ OpenClaw РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ", "Default OpenClaw model"),
            description = localizedText(
                language,
                "OpenClaw РёСЃРїРѕР»СЊР·СѓРµС‚ РѕС‚РґРµР»СЊРЅС‹Р№ РІС‹Р±РѕСЂ РјРѕРґРµР»Рё Рё РЅРµ РѕР±СЏР·Р°РЅ СЃРѕРІРїР°РґР°С‚СЊ СЃ РѕР±С‹С‡РЅС‹Рј С‡Р°С‚РѕРј.",
                "OpenClaw keeps its own default model and does not have to match ordinary chat."
            ),
        ) {
            ChoiceRow(
                items = modelOptions,
                isSelected = { option ->
                    option?.id == settings.preferredOpenClawModelId ||
                        (option == null && settings.preferredOpenClawModelId == null)
                },
                label = { option -> option?.modelName ?: localizedText(language, "РќРµ РІС‹Р±СЂР°РЅР°", "None") },
                onSelect = { option -> onPreferredModelSelected(option?.id) },
            )
        }
    }
    item {
        StandardCard(
            title = localizedText(language, "Р’С‹Р±СЂР°С‚СЊ РЅР°РІС‹РєРё", "Choose skills"),
            description = localizedText(
                language,
                "РќР°РІС‹РєРё, РєРѕС‚РѕСЂС‹Рµ OpenClaw Р±СѓРґРµС‚ РїСЂРёРѕСЂРёС‚РёР·РёСЂРѕРІР°С‚СЊ РІ Р°РіРµРЅС‚РЅРѕР№ СЃРµСЃСЃРёРё.",
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
            title = localizedText(language, "Р”РѕР±Р°РІРёС‚СЊ РёРЅСЃС‚СЂСѓРјРµРЅС‚С‹", "Add tools"),
            description = localizedText(
                language,
                "РРЅСЃС‚СЂСѓРјРµРЅС‚С‹, РєРѕС‚РѕСЂС‹Рµ OpenClaw РјРѕР¶РµС‚ РІС‹Р·С‹РІР°С‚СЊ РїРѕРІРµСЂС… Р°РєС‚РёРІРЅРѕР№ Р»РѕРєР°Р»СЊРЅРѕР№ РјРѕРґРµР»Рё.",
                "Choose which tools OpenClaw can call on top of the active local model."
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
            title = localizedText(language, "Р РµРєРѕРјРµРЅРґСѓРµРјР°СЏ РјРѕРґРµР»СЊ", "Recommended model"),
            description = OpenClawCatalog.RECOMMENDED_REPO,
        ) {
            Text(OpenClawCatalog.RECOMMENDED_MODEL_FILE, style = MaterialTheme.typography.bodySmall)
            Text(
                localizedText(
                    language,
                    "РћРїС†РёРѕРЅР°Р»СЊРЅС‹Р№ projector: ${OpenClawCatalog.RECOMMENDED_PROJECTOR_FILE}",
                    "Optional projector: ${OpenClawCatalog.RECOMMENDED_PROJECTOR_FILE}"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    item {
        SwitchRow(
            title = localizedText(language, "РђРІС‚РѕРјР°С‚РёС‡РµСЃРєРё СЃС‚Р°РІРёС‚СЊ СЂРµРєРѕРјРµРЅРґРѕРІР°РЅРЅСѓСЋ Gemma", "Auto-use the recommended Gemma"),
            description = localizedText(
                language,
                "РџРѕСЃР»Рµ СѓСЃС‚Р°РЅРѕРІРєРё СЃРґРµР»Р°С‚СЊ РµС‘ РѕР±С‹С‡РЅРѕР№ РјРѕРґРµР»СЊСЋ С‡Р°С‚Р° РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ.",
                "After install, make it the ordinary chat default."
            ),
            checked = settings.autoUseRecommendedModel,
            onCheckedChange = onAutoUseRecommendedChange,
        )
    }
    item {
        SwitchRow(
            title = localizedText(language, "РџСЂРµРґРїРѕС‡РёС‚Р°С‚СЊ mmproj РґР»СЏ live", "Prefer mmproj for live"),
            description = localizedText(
                language,
                "РСЃРїРѕР»СЊР·РѕРІР°С‚СЊ projector РґР»СЏ multimodal Рё live-СЃС†РµРЅР°СЂРёРµРІ.",
                "Use the projector for multimodal and live scenarios."
            ),
            checked = settings.preferMultimodalProjector,
            onCheckedChange = onPreferProjectorChange,
        )
    }
    item {
        SwitchRow(
            title = localizedText(language, "РџРѕРєР°Р·С‹РІР°С‚СЊ СЂР°СЃС€РёСЂРµРЅРЅС‹Рµ Р±СЌРєРµРЅРґС‹", "Show advanced backends"),
            description = localizedText(
                language,
                "LAN-РѕСЂРєРµСЃС‚СЂР°С†РёСЏ Рё РґРѕРїРѕР»РЅРёС‚РµР»СЊРЅС‹Рµ backend-РІР°СЂРёР°РЅС‚С‹ РїРѕРєР°Р·С‹РІР°СЋС‚СЃСЏ С‚РѕР»СЊРєРѕ РїРѕ СЏРІРЅРѕРјСѓ Р¶РµР»Р°РЅРёСЋ.",
                "LAN orchestration and extra backend options are shown only when explicitly enabled."
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
    item { SectionHeader(title = localizedText(language, "Р“РѕР»РѕСЃ Рё РѕР·РІСѓС‡РєР° РІ live", "Voice and live TTS")) }
    item {
        StandardCard(
            title = localizedText(language, "Р‘СЌРєРµРЅРґ РѕР·РІСѓС‡РєРё", "Voice backend"),
            description = localizedText(
                language,
                "Silero Рё Piper СЂР°Р±РѕС‚Р°СЋС‚ Р»РѕРєР°Р»СЊРЅРѕ, GPT-SoVITS Рё XTTS/AllTalk РїРѕРґРєР»СЋС‡Р°СЋС‚СЃСЏ РєР°Рє LAN-СѓР·Р»С‹.",
                "Silero and Piper run locally, while GPT-SoVITS and XTTS/AllTalk connect as LAN nodes."
            ),
        ) {
            ChoiceRow(
                items = VoiceRuntimeOption.entries.toList(),
                isSelected = { it == settings.selectedRuntime },
                label = {
                    when (it) {
                        VoiceRuntimeOption.SILERO_LOCAL -> localizedText(language, "Silero Р»РѕРєР°Р»СЊРЅРѕ", "Silero Local")
                        VoiceRuntimeOption.PIPER_LOCAL -> localizedText(language, "Piper Р»РѕРєР°Р»СЊРЅРѕ", "Piper Local")
                        VoiceRuntimeOption.GPT_SOVITS_NODE -> localizedText(language, "GPT-SoVITS С‡РµСЂРµР· LAN", "GPT-SoVITS LAN")
                        VoiceRuntimeOption.XTTS_ALLTALK_NODE -> localizedText(language, "XTTS / AllTalk С‡РµСЂРµР· LAN", "XTTS / AllTalk LAN")
                    }
                },
                onSelect = onRuntimeSelected,
            )
        }
    }
    item {
        SwitchRow(
            title = localizedText(language, "Р РµР·РµСЂРІРЅРѕ РїРµСЂРµС…РѕРґРёС‚СЊ РЅР° Р»РѕРєР°Р»СЊРЅС‹Р№ TTS", "Fallback to local TTS"),
            checked = settings.fallbackToLocal,
            onCheckedChange = onFallbackToLocalChange,
        )
    }
    item {
        SwitchRow(
            title = localizedText(language, "РџСЂРµРґРїРѕС‡РёС‚Р°С‚СЊ РєР»РѕРЅРёСЂРѕРІР°РЅРЅС‹Р№ РіРѕР»РѕСЃ", "Prefer cloned voice"),
            checked = settings.preferClonedVoice,
            onCheckedChange = onPreferClonedVoiceChange,
        )
    }
    if (settings.selectedRuntime == VoiceRuntimeOption.GPT_SOVITS_NODE ||
        settings.selectedRuntime == VoiceRuntimeOption.XTTS_ALLTALK_NODE
    ) {
        item {
            StandardCard(
                title = localizedText(language, "LAN-СѓР·Р»С‹ РѕР·РІСѓС‡РєРё", "LAN voice nodes"),
                description = localizedText(
                    language,
                    "Р­С‚Рё Р±СЌРєРµРЅРґС‹ СЂР°Р±РѕС‚Р°СЋС‚ РєР°Рє РІРЅРµС€РЅРёРµ LAN-СЃРµСЂРІРёСЃС‹, Р° РЅРµ РєР°Рє РІСЃС‚СЂРѕРµРЅРЅС‹Р№ Android runtime.",
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
    if (model.providerType == ProviderType.GOOGLE_LOCAL || model.providerType == ProviderType.AIRLLM_REMOTE) return true
    if (model.providerType != ProviderType.GGUF) return false
    val name = model.modelName.lowercase()
    if ("mmproj" in name) return false
    if ("vision" in name && "chat" !in name && "gemma" !in name) return false
    return true
}



