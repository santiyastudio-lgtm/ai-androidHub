package com.santiya.localaihub.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.santiya.localaihub.global.Standards
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
    viewModel: SettingsViewModel = viewModel()
) {
    // App settings
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
    val externalAccessPolicy by viewModel.externalAccessPolicy.collectAsStateWithLifecycle()
    val orchestraConfig by viewModel.orchestraConfig.collectAsStateWithLifecycle()
    val orchestraCapabilityState by viewModel.orchestraCapabilityState.collectAsStateWithLifecycle()
    val lanHubConfig by viewModel.lanHubConfig.collectAsStateWithLifecycle()
    val lanNodesJson by viewModel.lanNodesJson.collectAsStateWithLifecycle()
    val hardwareTuningEnabled by viewModel.hardwareTuningEnabled.collectAsStateWithLifecycle()
    val hardwareProfile by viewModel.hardwareProfile.collectAsStateWithLifecycle()
    val performanceMode by viewModel.performanceMode.collectAsStateWithLifecycle()
    val accelerationMode by viewModel.accelerationMode.collectAsStateWithLifecycle()
    // Installed models
    val installedModels by viewModel.installedModels.collectAsStateWithLifecycle(initialValue = emptyList())

    // Tool calling model state
    val hasToolCallingModel by viewModel.hasToolCallingModel.collectAsStateWithLifecycle()
    val toolCallingDownloadStates by viewModel.toolCallingModelDownloadState.collectAsStateWithLifecycle()
    val toolCallingDownloadState = toolCallingDownloadStates[PluginManager.TOOL_CALLING_MODEL_ID]


    // TTS settings
    val ttsSettings by viewModel.ttsSettings.collectAsStateWithLifecycle()
    val ttsModelLoaded by viewModel.ttsModelLoaded.collectAsStateWithLifecycle()
    val ttsVoices by viewModel.ttsAvailableVoices.collectAsStateWithLifecycle()
    val hasTtsModel by viewModel.hasTtsModel.collectAsStateWithLifecycle()
    val ttsDownloadStates by viewModel.ttsDownloadStates.collectAsStateWithLifecycle()
    val ttsDownloadState = ttsDownloadStates["supertonic-v2-tts"]

    // Auto-load TTS after download succeeds
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
                        "Настройки",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onNavigateBack,
                        icon = TnIcons.ArrowLeft,
                        contentDescription = "Назад"
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
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            // РІвЂќР‚РІвЂќР‚ General РІвЂќР‚РІвЂќР‚
            generalSettingsSection(
                toolCallingEnabled = toolCallingEnabled,
                toolCallingBypassEnabled = toolCallingBypassEnabled,
                hasToolCallingModel = hasToolCallingModel,
                toolCallingDownloadState = toolCallingDownloadState,
                viewModel = viewModel
            )

            themeSettingsSection(
                themePreset = themePreset,
                viewModel = viewModel
            )

            preferredModelsSection(
                installedModels = installedModels,
                preferredModels = preferredModels,
                viewModel = viewModel
            )

            externalAccessSection(
                externalAccessPolicy = externalAccessPolicy,
                viewModel = viewModel
            )

            lanSection(
                lanHubConfig = lanHubConfig,
                lanNodesJson = lanNodesJson,
                viewModel = viewModel
            )

            orchestraSection(
                orchestraConfig = orchestraConfig,
                orchestraCapabilityState = orchestraCapabilityState,
                viewModel = viewModel
            )

            // РІвЂќР‚РІвЂќР‚ LLM РІвЂќР‚РІвЂќР‚
            llmSettingsSection(
                streamingEnabled = streamingEnabled,
                chatMemoryEnabled = chatMemoryEnabled,
                askModelReloadDialog = askModelReloadDialog,
                viewModel = viewModel
            )

            // РІвЂќР‚РІвЂќР‚ Chat РІвЂќР‚РІвЂќР‚
            chatSettingsSection(
                codeHighlightEnabled = codeHighlightEnabled,
                viewModel = viewModel
            )

            // РІвЂќР‚РІвЂќР‚ Hardware Tuning РІвЂќР‚РІвЂќР‚
            hardwareTuningSection(
                hardwareTuningEnabled = hardwareTuningEnabled,
                performanceMode = performanceMode,
                accelerationMode = accelerationMode,
                hardwareProfile = hardwareProfile,
                viewModel = viewModel
            )

            // РІвЂќР‚РІвЂќР‚ Model Configuration РІвЂќР‚РІвЂќР‚
            modelConfigurationSection(
                hardwareTuningEnabled = hardwareTuningEnabled,
                installedModels = installedModels,
                onModelEditor = onModelEditor
            )

            // РІвЂќР‚РІвЂќР‚ AI Memory РІвЂќР‚РІвЂќР‚
            aiMemorySection(
                aiMemoryEnabled = aiMemoryEnabled,
                onAiMemoryClick = onAiMemoryClick,
                viewModel = viewModel
            )

            // РІвЂќР‚РІвЂќР‚ TTS РІвЂќР‚РІвЂќР‚
            ttsSettingsSection(
                hasTtsModel = hasTtsModel,
                ttsDownloadState = ttsDownloadState,
                ttsModelLoaded = ttsModelLoaded,
                loadTTSOnStart = loadTTSOnStart,
                ttsSettings = ttsSettings,
                voices = voices,
                viewModel = viewModel
            )

            // РІвЂќР‚РІвЂќР‚ Image Generation РІвЂќР‚РІвЂќР‚
            imageGenerationSection(
                imageBlurEnabled = imageBlurEnabled,
                viewModel = viewModel
            )

            // РІвЂќР‚РІвЂќР‚ Data Management РІвЂќР‚РІвЂќР‚
            item { Spacer(Modifier.height(Standards.SpacingSm)) }
            item { SectionDivider() }
            item { SectionHeader(title = "РЈРїСЂР°РІР»РµРЅРёРµ РґР°РЅРЅС‹РјРё") }

            item {
                DataManagementSection(viewModel = viewModel)
            }

            // РІвЂќР‚РІвЂќР‚ About РІвЂќР‚РІвЂќР‚
            aboutSection(appVersion = viewModel.appVersion)

            item { Spacer(Modifier.height(Standards.SpacingXl)) }
        }
    }
}
