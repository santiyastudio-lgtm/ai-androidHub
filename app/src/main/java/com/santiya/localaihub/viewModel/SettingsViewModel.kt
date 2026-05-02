package com.santiya.localaihub.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.santiya.localaihub.data.AppSettingsDataStore
import com.santiya.localaihub.di.AppContainer
import com.santiya.localaihub.global.AccelerationMode
import com.santiya.localaihub.global.DeviceTuner
import com.santiya.localaihub.global.HardwareProfile
import com.santiya.localaihub.global.HardwareScanner
import com.santiya.localaihub.global.PerformanceMode
import com.santiya.localaihub.hub.ExternalAccessPolicy
import com.santiya.localaihub.hub.LanCoordinator
import com.santiya.localaihub.hub.LanHubConfig
import com.santiya.localaihub.hub.ModelOrchestraManager
import com.santiya.localaihub.hub.OrchestraCapabilityState
import com.santiya.localaihub.hub.OrchestraConfig
import com.santiya.localaihub.hub.PreferredModelMap
import com.santiya.localaihub.hub.ThemePreset
import com.santiya.localaihub.models.engine_schema.GgufEngineSchema
import com.santiya.localaihub.models.enums.ProviderType
import com.santiya.localaihub.models.table_schema.Model
import com.santiya.localaihub.plugins.PluginManager
import com.santiya.localaihub.service.ModelDownloadService
import com.santiya.localaihub.state.AppStateManager
import com.santiya.localaihub.tts.TTSDataStore
import com.santiya.localaihub.tts.TTSManager
import com.santiya.localaihub.tts.TTSSettings
import com.santiya.localaihub.worker.DiffusionBackendSelector
import com.santiya.localaihub.worker.DiffusionConfig
import com.santiya.localaihub.worker.SystemBackupManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val profileJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val appSettingsDataStore = AppSettingsDataStore(application)
    private val ttsDataStore = TTSDataStore(application)
    private val orchestraManager = ModelOrchestraManager(application)
    private val lanCoordinator = LanCoordinator(application)
    private val modelStoreRepository = com.santiya.localaihub.repo.ModelStoreRepository(application)

    private val modelRepository = AppContainer.getModelRepository()

    init {
        // Sync bypass setting with PluginManager on startup
        viewModelScope.launch {
            appSettingsDataStore.toolCallingBypassEnabled.collect { enabled ->
                PluginManager.setToolCallingBypassEnabled(enabled)
            }
        }
    }

    // Installed models
    val installedModels: Flow<List<Model>> = modelRepository.getAllModels()

    // TTS install state
    val hasTtsModel: StateFlow<Boolean> = modelRepository.getAllModels()
        .map { models -> models.any { it.providerType == ProviderType.TTS || it.providerType == ProviderType.TTS_PIPER } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Tool calling model install state вЂ” any GGUF model can support tool calling
    // (actual compatibility is checked at load time via native chat-template detection)
    val hasToolCallingModel: StateFlow<Boolean> = modelRepository.getAllModels()
        .map { models ->
            models.any { model -> model.providerType == ProviderType.GGUF }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val ttsDownloadStates: StateFlow<Map<String, ModelDownloadService.DownloadState>> =
        ModelDownloadService.downloadStates

    // Tool calling model download state
    val toolCallingModelDownloadState: StateFlow<Map<String, ModelDownloadService.DownloadState>> =
        ModelDownloadService.downloadStates

    // App settings
    val streamingEnabled: StateFlow<Boolean> = appSettingsDataStore.streamingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val chatMemoryEnabled: StateFlow<Boolean> = appSettingsDataStore.chatMemoryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val toolCallingEnabled: StateFlow<Boolean> = appSettingsDataStore.toolCallingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val toolCallingBypassEnabled: StateFlow<Boolean> = appSettingsDataStore.toolCallingBypassEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val imageBlurEnabled: StateFlow<Boolean> = appSettingsDataStore.imageBlurEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val loadTTSOnStart: StateFlow<Boolean> = appSettingsDataStore.loadTTSOnStart
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val codeHighlightEnabled: StateFlow<Boolean> = appSettingsDataStore.codeHighlightEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val aiMemoryEnabled: StateFlow<Boolean> = appSettingsDataStore.aiMemoryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val askModelReloadDialog: StateFlow<Boolean> = appSettingsDataStore.askModelReloadDialog
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val themePreset: StateFlow<ThemePreset> = appSettingsDataStore.themePreset
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreset.SYSTEM)

    val preferredModels: StateFlow<PreferredModelMap> = appSettingsDataStore.preferredModels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreferredModelMap())

    val externalAccessPolicy: StateFlow<ExternalAccessPolicy> = appSettingsDataStore.externalAccessPolicy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExternalAccessPolicy())

    val orchestraConfig: StateFlow<OrchestraConfig> = appSettingsDataStore.orchestraConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OrchestraConfig())

    val lanHubConfig: StateFlow<LanHubConfig> = appSettingsDataStore.lanHubConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LanHubConfig())

    // Hardware tuning
    val hardwareTuningEnabled: StateFlow<Boolean> = appSettingsDataStore.hardwareTuningEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val hardwareProfile: StateFlow<HardwareProfile?> = appSettingsDataStore.hardwareProfileJson
        .map { json ->
            json?.takeIf { it.isNotBlank() }?.let {
                try {
                    profileJson.decodeFromString<HardwareProfile>(it)
                } catch (_: Exception) { null }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val performanceMode: StateFlow<PerformanceMode> = appSettingsDataStore.performanceMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PerformanceMode.BALANCED)

    val accelerationMode: StateFlow<AccelerationMode> = appSettingsDataStore.accelerationMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccelerationMode.AUTO)

    val orchestraCapabilityState: StateFlow<OrchestraCapabilityState> =
        modelRepository.getAllModels()
            .combine(appSettingsDataStore.orchestraConfig) { models, config ->
                orchestraManager.capabilityState(models, config)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OrchestraCapabilityState())

    val lanNodesJson: StateFlow<String> =
        modelRepository.getAllModels()
            .combine(appSettingsDataStore.lanHubConfig) { models, config ->
                val safeConfig = lanCoordinator.ensurePairingToken(config)
                lanCoordinator.nodesJson(models, safeConfig)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "[]")

    // TTS settings
    val ttsSettings: StateFlow<TTSSettings> = ttsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TTSSettings())

    val ttsModelLoaded: StateFlow<Boolean> = TTSManager.isModelLoaded
    val ttsAvailableVoices: StateFlow<List<String>> = TTSManager.availableVoices

    // App info
    val appVersion: String = try {
        val pInfo = application.packageManager.getPackageInfo(application.packageName, 0)
        pInfo.versionName ?: "1.0"
    } catch (_: Exception) {
        "1.0"
    }

    // App settings updaters
    fun setStreamingEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateStreamingEnabled(enabled) }
    }

    fun setChatMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateChatMemoryEnabled(enabled) }
    }

    fun setToolCallingEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateToolCallingEnabled(enabled) }
    }

    fun setToolCallingBypassEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsDataStore.updateToolCallingBypassEnabled(enabled)
            // Sync with PluginManager
            PluginManager.setToolCallingBypassEnabled(enabled)
        }
    }

    fun setImageBlurEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateImageBlurEnabled(enabled) }
    }

    fun setLoadTTSOnStart(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateLoadTTSOnStart(enabled) }
    }

    fun setCodeHighlightEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateCodeHighlightEnabled(enabled) }
    }

    fun setAiMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateAiMemoryEnabled(enabled) }
    }

    fun setAskModelReloadDialog(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateAskModelReloadDialog(enabled) }
    }

    fun setThemePreset(preset: ThemePreset) {
        viewModelScope.launch { appSettingsDataStore.saveThemePreset(preset) }
    }

    fun setPreferredModels(preferred: PreferredModelMap) {
        viewModelScope.launch { appSettingsDataStore.savePreferredModels(preferred) }
    }

    fun setPreferredModel(capability: String, modelId: String?) {
        viewModelScope.launch {
            val current = appSettingsDataStore.preferredModelsSnapshot()
            val updated = when (capability) {
                "chat" -> current.copy(chatModelId = modelId)
                "vision" -> current.copy(visionModelId = modelId)
                "image_generation" -> current.copy(imageGenerationModelId = modelId)
                "video_generation" -> current.copy(videoGenerationModelId = modelId)
                "tts" -> current.copy(ttsModelId = modelId)
                "files" -> current.copy(filesModelId = modelId)
                "assistant_live" -> current.copy(assistantLiveModelId = modelId)
                else -> current
            }
            appSettingsDataStore.savePreferredModels(updated)
        }
    }

    fun setExternalAccessEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = appSettingsDataStore.externalAccessPolicySnapshot()
            appSettingsDataStore.saveExternalAccessPolicy(current.copy(enabled = enabled))
        }
    }

    fun approveClient(packageName: String) {
        viewModelScope.launch {
            val current = appSettingsDataStore.externalAccessPolicySnapshot()
            if (packageName.isBlank()) return@launch
            val updated = current.copy(
                approvedApps = current.approvedApps.filterNot { it.packageName == packageName } +
                    com.santiya.localaihub.hub.ApprovedClientApp(
                        packageName = packageName,
                        appLabel = packageName,
                        approvedAtEpochMs = System.currentTimeMillis()
                    ),
                pendingPackages = current.pendingPackages.filterNot { it == packageName }
            )
            appSettingsDataStore.saveExternalAccessPolicy(updated)
        }
    }

    fun revokeClient(packageName: String) {
        viewModelScope.launch {
            val current = appSettingsDataStore.externalAccessPolicySnapshot()
            appSettingsDataStore.saveExternalAccessPolicy(
                current.copy(
                    approvedApps = current.approvedApps.filterNot { it.packageName == packageName }
                )
            )
        }
    }

    fun setLanEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = appSettingsDataStore.lanHubConfigSnapshot()
            appSettingsDataStore.saveLanHubConfig(
                lanCoordinator.ensurePairingToken(current.copy(enabled = enabled))
            )
        }
    }

    fun setAdvertiseLocalNode(enabled: Boolean) {
        viewModelScope.launch {
            val current = appSettingsDataStore.lanHubConfigSnapshot()
            appSettingsDataStore.saveLanHubConfig(
                lanCoordinator.ensurePairingToken(current.copy(advertiseLocalNode = enabled))
            )
        }
    }

    fun regenerateLanToken() {
        viewModelScope.launch {
            val current = appSettingsDataStore.lanHubConfigSnapshot()
            appSettingsDataStore.saveLanHubConfig(
                lanCoordinator.ensurePairingToken(current.copy(pairingToken = ""))
            )
        }
    }

    fun setOrchestraEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = appSettingsDataStore.orchestraConfigSnapshot()
            appSettingsDataStore.saveOrchestraConfig(current.copy(enabled = enabled))
        }
    }

    fun setOrchestraAutoAssign(enabled: Boolean) {
        viewModelScope.launch {
            val current = appSettingsDataStore.orchestraConfigSnapshot()
            appSettingsDataStore.saveOrchestraConfig(current.copy(autoAssign = enabled))
        }
    }

    fun setOrchestraLanSpillover(enabled: Boolean) {
        viewModelScope.launch {
            val current = appSettingsDataStore.orchestraConfigSnapshot()
            appSettingsDataStore.saveOrchestraConfig(current.copy(allowLanSpillover = enabled))
        }
    }

    fun setHardwareTuningEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsDataStore.updateHardwareTuningEnabled(enabled)
            // When re-enabling, retune all GGUF configs with current performance mode
            if (enabled) {
                val mode = appSettingsDataStore.performanceMode.firstOrNull() ?: PerformanceMode.BALANCED
                retuneAllGgufConfigs(mode)
                AppStateManager.requestModelReload()
            }
        }
    }

    fun setPerformanceMode(mode: PerformanceMode) {
        viewModelScope.launch {
            appSettingsDataStore.savePerformanceMode(mode)

            val tuningEnabled = appSettingsDataStore.hardwareTuningEnabled.firstOrNull() ?: true
            if (!tuningEnabled) return@launch

            retuneAllGgufConfigs(mode)
            AppStateManager.requestModelReload()
        }
    }

    fun setAccelerationMode(mode: AccelerationMode) {
        viewModelScope.launch {
            appSettingsDataStore.saveAccelerationMode(mode)
            applyAccelerationModeToInstalledDiffusionModels(mode)
            AppStateManager.requestModelReload()
        }
    }

    private suspend fun retuneAllGgufConfigs(mode: PerformanceMode) {
        withContext(Dispatchers.IO) {
            try {
                val profile = HardwareScanner.scan(getApplication())
                val allModels = modelRepository.getAllModels().first()

                for (model in allModels.filter { it.providerType == ProviderType.GGUF }) {
                    val config = modelRepository.getConfigByModelId(model.id) ?: continue
                    val modelSizeMB = ((model.fileSize ?: 0L) / (1024 * 1024)).toInt()
                    val newLoading = DeviceTuner.tune(profile, modelSizeMB, model.modelName, mode)
                    val schema = GgufEngineSchema(loadingParams = newLoading)
                    modelRepository.updateConfig(config.copy(modelLoadingParams = schema.toLoadingJson()))
                }
            } catch (e: Exception) {
                Log.e("SettingsVM", "Failed to retune configs", e)
            }
        }
    }

    private suspend fun applyAccelerationModeToInstalledDiffusionModels(mode: AccelerationMode) {
        withContext(Dispatchers.IO) {
            try {
                val isQualcommDevice = modelStoreRepository.isQualcommDevice()
                val allModels = modelRepository.getAllModels().first()
                allModels.filter { it.providerType == ProviderType.DIFFUSION }.forEach { model ->
                    val config = modelRepository.getConfigByModelId(model.id) ?: return@forEach
                    val current = DiffusionConfig.fromJson(config.modelLoadingParams)
                    val selection = DiffusionBackendSelector.resolve(
                        mode = mode,
                        isQualcommDevice = isQualcommDevice,
                        modelDir = File(model.modelPath)
                    )
                    val updated = current.copy(
                        runOnCpu = selection.runOnCpu,
                        useCpuClip = selection.useCpuClip
                    )
                    if (updated != current) {
                        modelRepository.updateConfig(
                            config.copy(modelLoadingParams = updated.toJson())
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("SettingsVM", "Failed to apply acceleration mode", e)
            }
        }
    }

    fun rescanHardware() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = HardwareScanner.scan(getApplication())
                val json = profileJson.encodeToString(profile)
                appSettingsDataStore.saveHardwareProfile(json)
            } catch (e: Exception) {
                Log.e("SettingsVM", "Hardware rescan failed", e)
            }
        }
    }

    // TTS settings updaters
    fun updateVoice(voice: String) {
        viewModelScope.launch { ttsDataStore.updateVoice(voice) }
    }

    fun updateSpeed(speed: Float) {
        viewModelScope.launch { ttsDataStore.updateSpeed(speed) }
    }

    fun updateSteps(steps: Int) {
        viewModelScope.launch { ttsDataStore.updateSteps(steps) }
    }

    fun updateLanguage(language: String) {
        viewModelScope.launch { ttsDataStore.updateLanguage(language) }
    }

    fun updateAutoSpeak(enabled: Boolean) {
        viewModelScope.launch { ttsDataStore.updateAutoSpeak(enabled) }
    }

    fun updateUseNNAPI(enabled: Boolean) {
        viewModelScope.launch {
            ttsDataStore.updateUseNNAPI(enabled)
            reloadTtsModelIfLoaded(enabled)
        }
    }

    // Downloads
    companion object {
        private const val TTS_MODEL_ID = "supertonic-v2-tts"
    }

    fun downloadTts() {
        val context = getApplication<Application>()
        val intent = Intent(context, ModelDownloadService::class.java).apply {
            action = ModelDownloadService.ACTION_START_DOWNLOAD
            putExtra(ModelDownloadService.EXTRA_MODEL_ID, TTS_MODEL_ID)
            putExtra(ModelDownloadService.EXTRA_MODEL_NAME, "Supertonic v2 TTS")
            putExtra(ModelDownloadService.EXTRA_FILE_URL, "https://huggingface.co/Supertone/supertonic-2/resolve/main")
            putExtra(ModelDownloadService.EXTRA_IS_ZIP, false)
            putExtra(ModelDownloadService.EXTRA_MODEL_TYPE, "TTS")
            putExtra(ModelDownloadService.EXTRA_RUN_ON_CPU, true)
            putExtra(ModelDownloadService.EXTRA_TEXT_EMBEDDING_SIZE, 0)
        }
        androidx.core.content.ContextCompat.startForegroundService(context, intent)
    }

    fun downloadToolCallingModel() {
        val context = getApplication<Application>()
        val model = PluginManager.TOOL_CALLING_MODEL
        val intent = Intent(context, ModelDownloadService::class.java).apply {
            action = ModelDownloadService.ACTION_START_DOWNLOAD
            putExtra(ModelDownloadService.EXTRA_MODEL_ID, model.id)
            putExtra(ModelDownloadService.EXTRA_MODEL_NAME, model.name)
            putExtra(ModelDownloadService.EXTRA_FILE_URL, "https://huggingface.co/${model.fileUri}")
            putExtra(ModelDownloadService.EXTRA_IS_ZIP, model.isZip)
            putExtra(ModelDownloadService.EXTRA_MODEL_TYPE, "GGUF")
            putExtra(ModelDownloadService.EXTRA_RUN_ON_CPU, model.runOnCpu)
            putExtra(ModelDownloadService.EXTRA_TEXT_EMBEDDING_SIZE, model.textEmbeddingSize)
        }
        androidx.core.content.ContextCompat.startForegroundService(context, intent)
    }

    fun loadTtsAfterDownload() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val modelDir = TTSManager.getModelDirectory() ?: return@withContext
                val useNNAPI = ttsDataStore.settings.first().useNNAPI
                TTSManager.loadModel(modelDir, useNNAPI)
            }
        }
    }

    private suspend fun reloadTtsModelIfLoaded(useNNAPI: Boolean) {
        withContext(Dispatchers.IO) {
            if (!TTSManager.isLoaded()) return@withContext
            val modelDir = TTSManager.getModelDirectory() ?: return@withContext
            TTSManager.loadModel(modelDir, useNNAPI)
        }
    }

    // ==================== Backup / Restore / Delete ====================

    private val _backupProgress = MutableStateFlow<SystemBackupManager.BackupProgress?>(null)
    val backupProgress: StateFlow<SystemBackupManager.BackupProgress?> = _backupProgress

    private val _backupOptions = MutableStateFlow(SystemBackupManager.BackupOptions())
    val backupOptions: StateFlow<SystemBackupManager.BackupOptions> = _backupOptions

    private val _backupSizeEstimate = MutableStateFlow<SystemBackupManager.BackupSizeEstimate?>(null)
    val backupSizeEstimate: StateFlow<SystemBackupManager.BackupSizeEstimate?> = _backupSizeEstimate

    fun updateBackupOptions(options: SystemBackupManager.BackupOptions) {
        _backupOptions.value = options
        estimateBackupSize(options)
    }

    fun estimateBackupSize(options: SystemBackupManager.BackupOptions = _backupOptions.value) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val manager = SystemBackupManager(getApplication())
                _backupSizeEstimate.value = manager.estimateBackupSize(options)
            } catch (e: Exception) {
                _backupSizeEstimate.value = null
            }
        }
    }

    fun createBackup(uri: Uri, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val manager = SystemBackupManager(getApplication())
            manager.createBackup(uri, password, _backupOptions.value) { progress ->
                _backupProgress.value = progress
            }
        }
    }

    fun restoreBackup(uri: Uri, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val manager = SystemBackupManager(getApplication())
            manager.restoreBackup(uri, password) { progress ->
                _backupProgress.value = progress
            }
        }
    }

    fun deleteAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            val manager = SystemBackupManager(getApplication())
            manager.deleteAllData { progress ->
                _backupProgress.value = progress
            }
        }
    }

    fun clearBackupProgress() {
        _backupProgress.value = null
    }
}
