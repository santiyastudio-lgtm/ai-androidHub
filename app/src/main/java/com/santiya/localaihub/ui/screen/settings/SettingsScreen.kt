package com.santiya.localaihub.ui.screen.settings

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.global.localizedText
import com.santiya.localaihub.plugins.PluginManager
import com.santiya.localaihub.service.ModelDownloadService
import com.santiya.localaihub.ui.components.ActionButton
import com.santiya.localaihub.ui.components.SectionDivider
import com.santiya.localaihub.ui.components.SectionHeader
import com.santiya.localaihub.ui.icons.TnIcons
import com.santiya.localaihub.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onModelEditor: () -> Unit = {},
    onAiMemoryClick: () -> Unit = {},
    onOfflineCityClick: () -> Unit = {},
    onApiModelsClick: () -> Unit = {},
    onBrowserClick: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    var settingsQuery by rememberSaveable { mutableStateOf("") }

    val streamingEnabled by viewModel.streamingEnabled.collectAsStateWithLifecycle()
    val chatMemoryEnabled by viewModel.chatMemoryEnabled.collectAsStateWithLifecycle()
    val toolCallingEnabled by viewModel.toolCallingEnabled.collectAsStateWithLifecycle()
    val toolCallingBypassEnabled by viewModel.toolCallingBypassEnabled.collectAsStateWithLifecycle()
    val imageBlurEnabled by viewModel.imageBlurEnabled.collectAsStateWithLifecycle()
    val loadTTSOnStart by viewModel.loadTTSOnStart.collectAsStateWithLifecycle()
    val codeHighlightEnabled by viewModel.codeHighlightEnabled.collectAsStateWithLifecycle()
    val aiMemoryEnabled by viewModel.aiMemoryEnabled.collectAsStateWithLifecycle()
    val askModelReloadDialog by viewModel.askModelReloadDialog.collectAsStateWithLifecycle()
    val themePreset by viewModel.themePreset.collectAsStateWithLifecycle()
    val preferredModels by viewModel.preferredModels.collectAsStateWithLifecycle()
    val orchestraConfig by viewModel.orchestraConfig.collectAsStateWithLifecycle()
    val orchestraCapabilityState by viewModel.orchestraCapabilityState.collectAsStateWithLifecycle()
    val lanHubConfig by viewModel.lanHubConfig.collectAsStateWithLifecycle()
    val lanNodesJson by viewModel.lanNodesJson.collectAsStateWithLifecycle()
    val appLanguageSettings by viewModel.appLanguageSettings.collectAsStateWithLifecycle()
    val openClawLocalSettings by viewModel.openClawLocalSettings.collectAsStateWithLifecycle()
    val voiceRuntimeSettings by viewModel.voiceRuntimeSettings.collectAsStateWithLifecycle()
    val hardwareTuningEnabled by viewModel.hardwareTuningEnabled.collectAsStateWithLifecycle()
    val hardwareProfile by viewModel.hardwareProfile.collectAsStateWithLifecycle()
    val performanceMode by viewModel.performanceMode.collectAsStateWithLifecycle()
    val accelerationMode by viewModel.accelerationMode.collectAsStateWithLifecycle()
    val installedModels by viewModel.installedModels.collectAsStateWithLifecycle(initialValue = emptyList())

    val hasToolCallingModel by viewModel.hasToolCallingModel.collectAsStateWithLifecycle()
    val toolCallingDownloadStates by viewModel.toolCallingModelDownloadState.collectAsStateWithLifecycle()
    val toolCallingDownloadState = toolCallingDownloadStates[PluginManager.TOOL_CALLING_MODEL_ID]

    val ttsSettings by viewModel.ttsSettings.collectAsStateWithLifecycle()
    val ttsModelLoaded by viewModel.ttsModelLoaded.collectAsStateWithLifecycle()
    val ttsVoices by viewModel.ttsAvailableVoices.collectAsStateWithLifecycle()
    val hasTtsModel by viewModel.hasTtsModel.collectAsStateWithLifecycle()
    val ttsDownloadStates by viewModel.ttsDownloadStates.collectAsStateWithLifecycle()
    val ttsDownloadState = ttsDownloadStates["supertonic-v2-tts"]

    LaunchedEffect(ttsDownloadState) {
        if (ttsDownloadState is ModelDownloadService.DownloadState.Success) {
            viewModel.loadTtsAfterDownload()
        }
    }

    val voices = ttsVoices.ifEmpty { DEFAULT_VOICES }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        localizedText("Настройки", "Settings"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onNavigateBack,
                        icon = TnIcons.ArrowLeft,
                        contentDescription = localizedText("Назад", "Back"),
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = Standards.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm),
        ) {
            item {
                Surface(color = MaterialTheme.colorScheme.background) {
                    OutlinedTextField(
                        value = settingsQuery,
                        onValueChange = { settingsQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Standards.SpacingSm),
                        label = { Text(localizedText("Поиск по настройкам", "Search settings")) },
                        singleLine = true,
                    )
                }
            }

            if (settingsMatches(settingsQuery, "support", "donate", "project", "поддерж", "донат")) {
                item { CleanSupportQuickSection() }
                item { DonateQuickSection() }
            }

            if (settingsMatches(settingsQuery, "язык", "language", "localization")) {
                languageSection(
                    settings = appLanguageSettings,
                    onSelect = {
                        viewModel.setAppLanguage(it)
                        (context as? Activity)?.recreate()
                    }
                )
            }

            if (settingsMatches(settingsQuery, "api", "cloud", "openai", "openrouter", "deepseek", "claude", "gemini")) {
                apiModelsSection(appLanguageSettings.language, onApiModelsClick)
            }

            if (settingsMatches(settingsQuery, "openclaw", "локал", "local", "backend", "gemma", "оркестр", "agent", "skills")) {
                openClawLocalSection(
                    language = appLanguageSettings.language,
                    settings = openClawLocalSettings,
                    installedModels = installedModels,
                    onBackendSelected = { viewModel.setOpenClawBackend(it) },
                    onEnabledByDefaultChange = { viewModel.setOpenClawEnabledByDefault(it) },
                    onPreferredModelSelected = { viewModel.setOpenClawPreferredModel(it) },
                    onSkillToggle = { viewModel.toggleOpenClawSkill(it) },
                    onApiToolToggle = { viewModel.toggleOpenClawApiTool(it) },
                    onAutoUseRecommendedChange = { viewModel.setOpenClawAutoUseRecommendedModel(it) },
                    onPreferProjectorChange = { viewModel.setOpenClawPreferProjector(it) },
                    onShowAdvancedBackendsChange = { viewModel.setOpenClawShowAdvancedBackends(it) }
                )
            }

            if (settingsMatches(settingsQuery, "voice", "tts", "голос", "silero", "piper", "xtts", "sovits")) {
                voiceRuntimeSection(
                    language = appLanguageSettings.language,
                    settings = voiceRuntimeSettings,
                    onRuntimeSelected = { viewModel.setVoiceRuntimeOption(it) },
                    onFallbackToLocalChange = { viewModel.setVoiceFallbackToLocal(it) },
                    onPreferClonedVoiceChange = { viewModel.setVoicePreferClonedVoice(it) },
                    onGptSovitsUrlChange = { viewModel.updateGptSovitsNodeUrl(it) },
                    onXttsUrlChange = { viewModel.updateXttsAllTalkNodeUrl(it) }
                )
            }

            if (settingsMatches(settingsQuery, "offline", "город", "city", "map", "bus", "карта", "travel")) {
                offlineAgentSection(
                    language = appLanguageSettings.language,
                    onOpenOfflineCity = onOfflineCityClick,
                )
            }

            if (settingsMatches(settingsQuery, "general", "общие", "tool", "plugin")) {
                generalSettingsSection(
                    toolCallingEnabled = toolCallingEnabled,
                    toolCallingBypassEnabled = toolCallingBypassEnabled,
                    hasToolCallingModel = hasToolCallingModel,
                    toolCallingDownloadState = toolCallingDownloadState,
                    viewModel = viewModel
                )
            }

            if (settingsMatches(settingsQuery, "theme", "mono", "тема", "оформление")) {
                themeSettingsSection(themePreset = themePreset, viewModel = viewModel)
            }

            if (settingsMatches(settingsQuery, "preferred", "models", "модели", "selector")) {
                preferredModelsSection(installedModels = installedModels, preferredModels = preferredModels, viewModel = viewModel)
            }

            if (settingsMatches(settingsQuery, "lan", "node", "узел", "hub")) {
                lanSection(lanHubConfig = lanHubConfig, lanNodesJson = lanNodesJson, viewModel = viewModel)
            }

            if (settingsMatches(settingsQuery, "orchestra", "оркестр")) {
                orchestraSection(orchestraConfig = orchestraConfig, orchestraCapabilityState = orchestraCapabilityState, viewModel = viewModel)
            }

            if (settingsMatches(settingsQuery, "llm", "stream", "chat", "memory", "reload", "чат")) {
                llmSettingsSection(
                    streamingEnabled = streamingEnabled,
                    chatMemoryEnabled = chatMemoryEnabled,
                    askModelReloadDialog = askModelReloadDialog,
                    viewModel = viewModel
                )
            }

            if (settingsMatches(settingsQuery, "highlight", "code", "код")) {
                chatSettingsSection(codeHighlightEnabled = codeHighlightEnabled, viewModel = viewModel)
            }

            if (settingsMatches(settingsQuery, "hardware", "performance", "acceleration", "железо", "cpu", "gpu")) {
                hardwareTuningSection(
                    hardwareTuningEnabled = hardwareTuningEnabled,
                    performanceMode = performanceMode,
                    accelerationMode = accelerationMode,
                    hardwareProfile = hardwareProfile,
                    viewModel = viewModel
                )
            }

            if (settingsMatches(settingsQuery, "config", "editor", "engine", "runtime")) {
                modelConfigurationSection(
                    hardwareTuningEnabled = hardwareTuningEnabled,
                    installedModels = installedModels,
                    onModelEditor = onModelEditor
                )
            }

            if (settingsMatches(settingsQuery, "vault", "memory", "память")) {
                aiMemorySection(
                    aiMemoryEnabled = aiMemoryEnabled,
                    onAiMemoryClick = onAiMemoryClick,
                    viewModel = viewModel
                )
            }

            if (settingsMatches(settingsQuery, "tts", "voice", "silero", "piper", "голос")) {
                ttsSettingsSection(
                    language = appLanguageSettings.language,
                    hasTtsModel = hasTtsModel,
                    ttsDownloadState = ttsDownloadState,
                    ttsModelLoaded = ttsModelLoaded,
                    loadTTSOnStart = loadTTSOnStart,
                    ttsSettings = ttsSettings,
                    voices = voices,
                    viewModel = viewModel
                )
            }

            if (settingsMatches(settingsQuery, "image", "blur", "картин", "генерация")) {
                imageGenerationSection(imageBlurEnabled = imageBlurEnabled, viewModel = viewModel)
            }

            if (settingsMatches(settingsQuery, "backup", "restore", "support", "donate", "данные", "лог")) {
                item { Spacer(Modifier.height(Standards.SpacingSm)) }
                item { SectionDivider() }
                item { SectionHeader(title = localizedText("Управление данными", "Data management")) }
                item { DataManagementSection(viewModel = viewModel) }
            }

            if (settingsMatches(settingsQuery, "about", "version", "о приложении", "версия")) {
                aboutSection(appVersion = viewModel.appVersion)
            }

            item { Spacer(Modifier.height(Standards.SpacingXl)) }
        }
    }
}

private fun settingsMatches(query: String, vararg keywords: String): Boolean {
    if (query.isBlank()) return true
    val normalized = query.trim().lowercase()
    return keywords.any { keyword ->
        val lowered = keyword.lowercase()
        lowered.contains(normalized) || normalized.contains(lowered)
    }
}
