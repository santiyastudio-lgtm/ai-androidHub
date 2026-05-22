package com.santiya.localaihub.viewmodel

import android.app.Application
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.santiya.localaihub.data.ActiveModelActivationState
import com.santiya.localaihub.data.ActiveModelInstallStage
import com.santiya.localaihub.data.ActiveModelState
import com.santiya.localaihub.data.AppSettingsDataStore
import com.santiya.localaihub.data.VaultManager
import com.santiya.localaihub.di.AppContainer
import com.santiya.localaihub.global.AppPaths
import com.santiya.localaihub.hub.OpenClawLocalSettingsStore
import com.santiya.localaihub.models.enums.PathType
import com.santiya.localaihub.models.enums.ProviderType
import com.santiya.localaihub.models.table_schema.Model
import com.santiya.localaihub.models.table_schema.ModelConfig
import com.santiya.localaihub.repo.ModelStoreRepository
import com.santiya.localaihub.state.AppStateManager
import com.santiya.localaihub.storage.SharedModelLibrary
import com.santiya.localaihub.worker.DiffusionBackendSelector
import com.santiya.localaihub.worker.DiffusionConfig
import com.santiya.localaihub.worker.GgufProbeGuard
import com.santiya.localaihub.worker.GgufRuntimeSupport
import com.santiya.localaihub.worker.GoogleLocalSupport
import com.santiya.localaihub.worker.LlmModelWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LLMModelViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "LLMModelVM"
    }

    private val appSettings = AppSettingsDataStore(application)
    private val modelStoreRepository = ModelStoreRepository(application)
    private val openClawSettingsStore = OpenClawLocalSettingsStore(application)

    private val repository get() = AppContainer.getModelRepository()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val installedModels: Flow<List<Model>> = VaultManager.isReady
        .flatMapLatest { ready ->
            if (ready) repository.getAllModels() else flowOf(emptyList())
        }
        .map { models ->
            models.filter { model ->
                model.providerType == ProviderType.GGUF ||
                    model.providerType == ProviderType.GOOGLE_LOCAL ||
                    model.providerType == ProviderType.AIRLLM_REMOTE ||
                    model.providerType == ProviderType.DIFFUSION
            }
        }

    private val _currentModelID = MutableStateFlow("")
    val currentModelID: StateFlow<String> = _currentModelID.asStateFlow()

    private val _currentModelType = MutableStateFlow<ProviderType?>(null)

    private val _activeModelState = MutableStateFlow(ActiveModelState())
    val activeModelState: StateFlow<ActiveModelState> = _activeModelState.asStateFlow()
    val currentModelName: StateFlow<String?> = activeModelState
        .map { it.modelName }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _lastModelOffer = MutableStateFlow<Model?>(null)
    val lastModelOffer: StateFlow<Model?> = _lastModelOffer.asStateFlow()

    private val _pendingDiffusionModel = MutableStateFlow<Model?>(null)
    private val _needsQnnSetup = MutableStateFlow(false)
    val needsQnnSetup: StateFlow<Boolean> = _needsQnnSetup.asStateFlow()

    private fun isQnnRuntimeReady(): Boolean = try {
        val runtimeDir = File(getApplication<Application>().filesDir, "runtime_libs/qnnlibs")
        val marker = File(runtimeDir, ".extracted")
        marker.exists() && (runtimeDir.listFiles()?.size ?: 0) > 1
    } catch (_: Exception) {
        false
    }

    fun onQnnSetupComplete() {
        _needsQnnSetup.value = false
        val pending = _pendingDiffusionModel.value ?: return
        _pendingDiffusionModel.value = null
        loadModel(pending)
    }

    fun onQnnSetupDismissed() {
        _needsQnnSetup.value = false
        _pendingDiffusionModel.value = null
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            appSettings.activeModelState
                .distinctUntilChanged()
                .collect { persisted ->
                    _activeModelState.value = persisted
                    if (persisted.installStage == ActiveModelInstallStage.ACTIVATED) {
                        _currentModelID.value = persisted.modelId.orEmpty()
                        _currentModelType.value = persisted.providerTypeName?.let {
                            runCatching { ProviderType.valueOf(it) }.getOrNull()
                        }
                        if (!persisted.modelName.isNullOrBlank()) {
                            AppStateManager.setModelLoaded(persisted.modelName)
                        }
                    } else if (_currentModelID.value.isBlank()) {
                        _currentModelType.value = persisted.providerTypeName?.let {
                            runCatching { ProviderType.valueOf(it) }.getOrNull()
                        }
                        if (!persisted.hasSelection) {
                            _currentModelType.value = null
                        }
                    }
                }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val persistedSelection = appSettings.activeModelStateSnapshot()
            val savedId = persistedSelection.modelId
                ?: appSettings.lastModelId.first()
                ?: openClawSettingsStore.read().takeIf { it.enabledByDefault }?.preferredOpenClawModelId
                ?: return@launch
            val runtimeModelId =
                LlmModelWorker.currentGoogleLocalModelId.value
                    ?: LlmModelWorker.currentGgufModelId.value
                    ?: LlmModelWorker.currentDiffusionModelId.value
            val hasActiveRuntimeSelection = runtimeModelId == savedId
            if (hasActiveRuntimeSelection) return@launch
            val model = repository.getModelById(savedId) ?: return@launch
            if (!model.isActive) return@launch
            try {
                LlmModelWorker.ensureServiceReady()
            } catch (_: Exception) {
                return@launch
            }

            if (persistedSelection.hasSelection && persistedSelection.modelId == savedId) {
                loadModel(model)
                return@launch
            }

            val askDialog = appSettings.askModelReloadDialog.first()
            if (askDialog) {
                _lastModelOffer.value = model
            } else {
                loadModel(model)
            }
        }

        viewModelScope.launch {
            AppStateManager.reloadModelRequested.collect { requested ->
                if (requested && _currentModelID.value.isNotEmpty()) {
                    AppStateManager.clearReloadRequest()
                    reloadCurrentModel()
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            combine(
                LlmModelWorker.currentGgufModelId,
                LlmModelWorker.currentGoogleLocalModelId,
                LlmModelWorker.currentDiffusionModelId,
            ) { ggufId, googleId, diffusionId ->
                when {
                    googleId != null -> googleId to ProviderType.GOOGLE_LOCAL
                    ggufId != null -> ggufId to ProviderType.GGUF
                    diffusionId != null -> diffusionId to ProviderType.DIFFUSION
                    else -> null
                }
            }.collect { active ->
                if (active == null) {
                    val persisted = _activeModelState.value
                    if (persisted.providerTypeName == ProviderType.AIRLLM_REMOTE.name &&
                        persisted.installStage == ActiveModelInstallStage.ACTIVATED
                    ) {
                        return@collect
                    }
                    if (persisted.installStage == ActiveModelInstallStage.ACTIVATED) {
                        setActiveModelState(
                            persisted.copy(
                                installStage = ActiveModelInstallStage.SELECTED,
                                activationState = ActiveModelActivationState.IDLE,
                                activationReason = null,
                            )
                        )
                    } else {
                        _currentModelType.value = null
                    }
                } else {
                    val model = repository.getModelById(active.first)
                    setActiveModelState(
                        ActiveModelState(
                            modelId = active.first,
                            modelName = model?.modelName ?: _activeModelState.value.modelName,
                            providerTypeName = active.second.name,
                            installStage = ActiveModelInstallStage.ACTIVATED,
                            activationState = ActiveModelActivationState.ACTIVE,
                        )
                    )
                }
            }
        }
    }

    private suspend fun setActiveModelState(state: ActiveModelState) {
        _activeModelState.value = state
        _currentModelID.value = state.modelId.orEmpty()
        _currentModelType.value = state.providerTypeName?.let { runCatching { ProviderType.valueOf(it) }.getOrNull() }
        appSettings.saveActiveModelState(state)
        when {
            state.installStage == ActiveModelInstallStage.ACTIVATED && !state.modelName.isNullOrBlank() -> {
                AppStateManager.setModelLoaded(state.modelName)
            }
            !state.hasSelection -> AppStateManager.setModelUnloaded()
        }
    }

    private suspend fun setBlockedState(
        model: Model,
        providerType: ProviderType = model.providerType,
        reason: String,
        stage: ActiveModelInstallStage = ActiveModelInstallStage.INDEXED,
    ) {
        setActiveModelState(
            ActiveModelState(
                modelId = model.id,
                modelName = model.modelName,
                providerTypeName = providerType.name,
                installStage = stage,
                activationState = ActiveModelActivationState.BLOCKED,
                activationReason = reason,
            )
        )
        AppStateManager.setError(reason)
    }

    fun dismissLastModelOffer() {
        _lastModelOffer.value = null
    }

    fun acceptLastModelOffer() {
        val model = _lastModelOffer.value ?: return
        _lastModelOffer.value = null
        loadModel(model)
    }

    fun resumeSelectedModelIfNeeded() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_currentModelID.value.isNotEmpty()) return@launch

            val persisted = appSettings.activeModelStateSnapshot()
            if (!persisted.hasSelection) return@launch

            val modelId = persisted.modelId ?: appSettings.lastModelId.first() ?: return@launch
            val askDialog = appSettings.askModelReloadDialog.first()
            if (askDialog) return@launch

            val model = repository.getModelById(modelId) ?: return@launch
            if (!model.isActive) return@launch

            try {
                LlmModelWorker.ensureServiceReady()
            } catch (_: Exception) {
                return@launch
            }

            if (_currentModelID.value.isEmpty()) {
                loadModel(model)
            }
        }
    }

    val isGgufModelLoaded = LlmModelWorker.isGgufModelLoaded
    val isGoogleLocalModelLoaded = LlmModelWorker.isGoogleLocalModelLoaded
    val isDiffusionModelLoaded = LlmModelWorker.isDiffusionModelLoaded

    suspend fun getModelConfig(modelId: String): ModelConfig? = repository.getConfigByModelId(modelId)

    fun loadModel(model: Model) {
        Log.i(TAG, "loadModel requested: id=${model.id}, name=${model.modelName}, provider=${model.providerType}, path=${model.modelPath}")
        if (model.providerType == ProviderType.DIFFUSION && !isQnnRuntimeReady()) {
            _pendingDiffusionModel.value = model
            _needsQnnSetup.value = true
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                setActiveModelState(
                    ActiveModelState(
                        modelId = model.id,
                        modelName = model.modelName,
                        providerTypeName = model.providerType.name,
                        installStage = ActiveModelInstallStage.SELECTED,
                        activationState = ActiveModelActivationState.IDLE,
                    )
                )

                if (_currentModelID.value.isNotEmpty() && _currentModelID.value != model.id) {
                    unloadCurrentModel()
                    delay(300)
                }

                AppStateManager.setLoadingModel(model.modelName, 0f)

                val config = getModelConfig(model.id)
                if (config == null) {
                    setBlockedState(model, reason = "Не найдена конфигурация модели.", stage = ActiveModelInstallStage.IMPORTED)
                    return@launch
                }

                when (model.providerType) {
                    ProviderType.GGUF -> loadGgufModel(model, config)
                    ProviderType.GOOGLE_LOCAL -> loadGoogleLocalModel(model)
                    ProviderType.AIRLLM_REMOTE -> loadAirLlmRemoteModel(model)
                    ProviderType.DIFFUSION -> loadDiffusionModel(model, config)
                    ProviderType.TTS,
                    ProviderType.TTS_PIPER,
                    ProviderType.ONNX,
                    ProviderType.RAW_ASSET -> {
                        setBlockedState(
                            model = model,
                            reason = "Эта модель импортирована, но не подключена к основному chat runtime."
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadModel failed for ${model.id}: ${e.message}", e)
                setBlockedState(model, reason = e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun loadGgufModel(model: Model, config: ModelConfig) {
        var effectiveModel = model
        if (model.pathType == PathType.FILE) {
            val healed = SharedModelLibrary.resolveManagedRuntimeModel(getApplication(), model)
            if (healed != null && healed.modelPath != model.modelPath) {
                Log.i(TAG, "Healed GGUF model path: old=${model.modelPath}, new=${healed.modelPath}")
                runCatching { AppContainer.getModelRepository().updateModel(healed) }
                    .onFailure { Log.w(TAG, "Failed to persist healed GGUF path: ${it.message}") }
                effectiveModel = healed
            }
        }

        Log.i(TAG, "loadGgufModel start: id=${effectiveModel.id}, pathType=${effectiveModel.pathType}, path=${effectiveModel.modelPath}")
        if (GoogleLocalSupport.shouldPreferForGemma(getApplication(), effectiveModel.id, effectiveModel.modelName)) {
            Log.i(TAG, "Redirecting ${effectiveModel.modelName} to Google Local runtime")
            loadGoogleLocalModel(effectiveModel)
            return
        }

        if (effectiveModel.pathType != PathType.CONTENT_URI) {
            val runtimeFile = File(effectiveModel.modelPath)
            val compatibility = GgufRuntimeSupport.inspect(runtimeFile)
            if (!compatibility.supported) {
                setBlockedState(
                    model = effectiveModel,
                    providerType = ProviderType.GGUF,
                    reason = compatibility.message
                        ?: GgufRuntimeSupport.unsupportedArchitectureMessage(compatibility.architecture)
                )
                return
            }
            val probeVerdict = GgufProbeGuard.evaluate(getApplication(), runtimeFile)
            if (!probeVerdict.canProbeNow) {
                setBlockedState(
                    model = effectiveModel,
                    providerType = ProviderType.GGUF,
                    reason = probeVerdict.message ?: "Недостаточно памяти для безопасной загрузки GGUF-модели."
                )
                return
            }
            Log.i(TAG, "GGUF compatibility ok: arch=${compatibility.architecture}, backend=${compatibility.backend}")
        }

        val success = if (effectiveModel.pathType == PathType.CONTENT_URI) {
            val uri = effectiveModel.modelPath.toUri()
            LlmModelWorker.loadGgufModelFromUri(
                context = getApplication(),
                uri = uri,
                modelName = effectiveModel.modelName,
                modelConfig = config
            )
        } else {
            LlmModelWorker.loadGgufModel(effectiveModel, config)
        }

        if (success) {
            LlmModelWorker.setCurrentGgufModelId(effectiveModel.id)
            setActiveModelState(
                ActiveModelState(
                    modelId = effectiveModel.id,
                    modelName = effectiveModel.modelName,
                    providerTypeName = ProviderType.GGUF.name,
                    installStage = ActiveModelInstallStage.ACTIVATED,
                    activationState = ActiveModelActivationState.ACTIVE,
                )
            )
            appSettings.saveLastModelId(effectiveModel.id)

            try {
                val cacheDir = AppPaths.promptCache(getApplication<Application>()).also { it.mkdirs() }
                LlmModelWorker.setPromptCacheDirGguf(cacheDir.absolutePath)
                LlmModelWorker.setSpeculativeDecodingGguf(false)
                LlmModelWorker.warmUpGguf()
            } catch (e: Exception) {
                Log.w(TAG, "Optimization wiring failed: ${e.message}")
            }

            val nativeSupports = LlmModelWorker.isToolCallingSupportedGguf()
            com.santiya.localaihub.plugins.PluginManager.setToolCallingModelLoaded(nativeSupports)
            com.santiya.localaihub.plugins.PluginManager.syncToolsWithLLM()
        } else {
            setBlockedState(
                model = effectiveModel,
                providerType = ProviderType.GGUF,
                reason = "Не удалось активировать GGUF-модель."
            )
        }
    }

    private suspend fun loadGoogleLocalModel(model: Model) {
        val descriptor = GoogleLocalSupport.buildGemmaDescriptor(model.id, model.modelName)
        val success = LlmModelWorker.loadGoogleLocalModel(getApplication(), descriptor)
        if (success) {
            LlmModelWorker.setCurrentGoogleLocalModelId(model.id)
            LlmModelWorker.setCurrentGgufModelId(null)
            setActiveModelState(
                ActiveModelState(
                    modelId = model.id,
                    modelName = model.modelName,
                    providerTypeName = ProviderType.GOOGLE_LOCAL.name,
                    installStage = ActiveModelInstallStage.ACTIVATED,
                    activationState = ActiveModelActivationState.ACTIVE,
                )
            )
            appSettings.saveLastModelId(model.id)
            com.santiya.localaihub.plugins.PluginManager.setToolCallingModelLoaded(false)
            com.santiya.localaihub.plugins.PluginManager.syncToolsWithLLM()
        } else {
            setBlockedState(
                model = model,
                providerType = ProviderType.GOOGLE_LOCAL,
                reason = "Не удалось активировать Google Local для ${model.modelName}."
            )
        }
    }

    private suspend fun loadAirLlmRemoteModel(model: Model) {
        setActiveModelState(
            ActiveModelState(
                modelId = model.id,
                modelName = model.modelName,
                providerTypeName = ProviderType.AIRLLM_REMOTE.name,
                installStage = ActiveModelInstallStage.ACTIVATED,
                activationState = ActiveModelActivationState.ACTIVE,
            )
        )
        appSettings.saveLastModelId(model.id)
        AppStateManager.setModelLoaded("${model.modelName} (AirLLM gateway)")
        com.santiya.localaihub.plugins.PluginManager.setToolCallingModelLoaded(false)
        com.santiya.localaihub.plugins.PluginManager.togglePlugin("AirLLM Gateway", true)
    }

    private suspend fun loadDiffusionModel(model: Model, config: ModelConfig) {
        val storedConfig = DiffusionConfig.fromJson(config.modelLoadingParams)
        val accelerationMode = appSettings.accelerationMode.first()
        val selection = DiffusionBackendSelector.resolve(
            mode = accelerationMode,
            isQualcommDevice = modelStoreRepository.isQualcommDevice(),
            modelDir = File(model.modelPath)
        )
        val diffusionConfig = storedConfig.copy(
            runOnCpu = selection.runOnCpu,
            useCpuClip = selection.useCpuClip
        )

        if (diffusionConfig != storedConfig) {
            repository.updateConfig(config.copy(modelLoadingParams = diffusionConfig.toJson()))
        }

        val success = LlmModelWorker.loadDiffusionModel(
            name = model.modelName,
            modelDir = model.modelPath,
            height = diffusionConfig.height,
            width = diffusionConfig.width,
            textEmbeddingSize = diffusionConfig.textEmbeddingSize,
            runOnCpu = diffusionConfig.runOnCpu,
            useCpuClip = diffusionConfig.useCpuClip,
            isPony = diffusionConfig.isPony,
            httpPort = diffusionConfig.httpPort,
            safetyMode = diffusionConfig.safetyMode
        )

        if (success) {
            LlmModelWorker.setCurrentDiffusionModelId(model.id)
            setActiveModelState(
                ActiveModelState(
                    modelId = model.id,
                    modelName = model.modelName,
                    providerTypeName = ProviderType.DIFFUSION.name,
                    installStage = ActiveModelInstallStage.ACTIVATED,
                    activationState = ActiveModelActivationState.ACTIVE,
                )
            )
            appSettings.saveLastModelId(model.id)
        } else {
            setBlockedState(model, providerType = ProviderType.DIFFUSION, reason = "Не удалось загрузить diffusion-модель.")
        }
    }

    private suspend fun unloadCurrentModel() {
        try {
            when (_currentModelType.value) {
                ProviderType.GGUF -> {
                    LlmModelWorker.unloadGgufModel()
                    LlmModelWorker.setCurrentGgufModelId(null)
                }
                ProviderType.GOOGLE_LOCAL -> {
                    LlmModelWorker.unloadGoogleLocalModel()
                    LlmModelWorker.setCurrentGoogleLocalModelId(null)
                }
                ProviderType.DIFFUSION -> {
                    LlmModelWorker.stopDiffusionBackend()
                    LlmModelWorker.setCurrentDiffusionModelId(null)
                }
                ProviderType.AIRLLM_REMOTE -> {
                    com.santiya.localaihub.plugins.PluginManager.togglePlugin("AirLLM Gateway", false)
                }
                else -> Unit
            }
            setActiveModelState(ActiveModelState())
            com.santiya.localaihub.plugins.PluginManager.setToolCallingModelLoaded(false)
        } catch (e: Exception) {
            Log.e(TAG, "unloadCurrentModel failed: ${e.message}", e)
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            try {
                when (_currentModelType.value) {
                    ProviderType.GGUF -> {
                        LlmModelWorker.unloadGgufModel()
                        LlmModelWorker.setCurrentGgufModelId(null)
                    }
                    ProviderType.GOOGLE_LOCAL -> {
                        LlmModelWorker.unloadGoogleLocalModel()
                        LlmModelWorker.setCurrentGoogleLocalModelId(null)
                    }
                    ProviderType.DIFFUSION -> {
                        LlmModelWorker.stopDiffusionBackend()
                        LlmModelWorker.setCurrentDiffusionModelId(null)
                    }
                    ProviderType.AIRLLM_REMOTE -> {
                        com.santiya.localaihub.plugins.PluginManager.togglePlugin("AirLLM Gateway", false)
                    }
                    else -> Unit
                }
                setActiveModelState(ActiveModelState())
                AppStateManager.setModelUnloaded()
            } catch (e: Exception) {
                AppStateManager.setError(e.message ?: "Failed to unload model")
            }
        }
    }

    fun reloadCurrentModel() {
        val modelId = _currentModelID.value
        if (modelId.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val model = repository.getModelById(modelId) ?: return@launch
            loadModel(model)
        }
    }

    fun deleteModel(model: Model, deleteFile: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (_currentModelID.value == model.id) {
                    unloadCurrentModel()
                    delay(300)
                }

                try {
                    val lastModelId = appSettings.lastModelId.first()
                    if (lastModelId == model.id) {
                        appSettings.saveLastModelId(null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear last model ID: ${e.message}")
                }

                val config = repository.getConfigByModelId(model.id)
                if (config != null) {
                    repository.deleteConfig(config)
                }

                if (deleteFile) {
                    try {
                        when (model.pathType) {
                            PathType.CONTENT_URI -> {
                                val deleted = SharedModelLibrary.deleteManagedModel(getApplication(), model)
                                Log.d(TAG, "Deleted shared model file: $deleted - ${model.modelPath}")
                            }
                            PathType.FILE, PathType.DIRECTORY -> {
                                val modelFile = File(model.modelPath)
                                if (SharedModelLibrary.isManagedSharedPath(model.modelPath)) {
                                    val deleted = SharedModelLibrary.deleteManagedModel(getApplication(), model)
                                    Log.d(TAG, "Deleted shared model file: $deleted - ${model.modelPath}")
                                } else if (modelFile.exists()) {
                                    if (modelFile.isDirectory) modelFile.deleteRecursively() else modelFile.delete()
                                    Log.d(TAG, "Deleted model file: ${model.modelPath}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete model file: ${e.message}")
                    }
                }

                repository.deleteModel(model)
                if (_activeModelState.value.modelId == model.id) {
                    setActiveModelState(ActiveModelState())
                }
                Log.d(TAG, "Model deleted: ${model.modelName}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete model: ${e.message}")
                AppStateManager.setError("Failed to delete model: ${e.message}")
            }
        }
    }
}
