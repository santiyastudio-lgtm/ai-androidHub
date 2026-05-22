package com.santiya.localaihub.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santiya.localaihub.data.AppSettingsDataStore
import com.santiya.localaihub.data.ActiveModelActivationState
import com.santiya.localaihub.airllm.AirLlmGatewayClient
import com.santiya.localaihub.openclaw.OfficialOpenClawGatewayClient
import com.santiya.localaihub.di.AppContainer
import com.santiya.localaihub.engine.GenerationEvent
import com.santiya.localaihub.browser.BrowserSessionSnapshot
import com.santiya.localaihub.browser.BrowserToolState
import com.santiya.localaihub.models.engine_schema.GgufEngineSchema
import com.santiya.localaihub.models.engine_schema.GgufInferenceParams
import com.santiya.localaihub.models.enums.ProviderType
import com.santiya.localaihub.models.messages.ContentType
import com.santiya.localaihub.models.messages.ImageGenerationMetrics
import com.santiya.localaihub.models.messages.MessageContent
import com.santiya.localaihub.models.messages.Messages
import com.santiya.localaihub.models.messages.RagResultItem
import com.santiya.localaihub.models.messages.Role
import com.santiya.localaihub.models.messages.ToolChainStepData
import com.santiya.localaihub.models.plugins.PluginExecutionMetrics
import com.santiya.localaihub.models.plugins.PluginResultData
import com.santiya.localaihub.plugins.PluginManager
import com.santiya.localaihub.state.AppStateManager
import com.santiya.localaihub.worker.ChatManager
import com.santiya.localaihub.worker.GoogleLocalSupport
import com.santiya.localaihub.worker.DiffusionConfig
import com.santiya.localaihub.worker.DiffusionInferenceParams
import com.santiya.localaihub.models.ModelType
import com.santiya.localaihub.tts.TTSManager
import com.santiya.localaihub.tts.TTSSettings
import com.santiya.localaihub.worker.LlmModelWorker
import com.santiya.localaihub.models.engine_schema.DecodingMetrics
import com.santiya.localaihub.offlinecity.OfflineCityAnswer
import com.santiya.localaihub.offlinecity.OfflineCityAssistant
import com.santiya.localaihub.offlinecity.OfflineCityLocationProvider
import com.santiya.localaihub.offlinecity.OfflineCityStorage
import com.santiya.localaihub.hub.LocalBackendOption
import com.santiya.localaihub.hub.OpenClawLocalSettingsStore
import com.santiya.googlelocalruntime.GoogleLocalMessage
import com.dark.gguf_lib.toolcalling.ToolCall
import com.dark.gguf_lib.toolcalling.ToolCallingConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class AgentPhase { Idle, Planning, Executing, Summarizing, Complete }

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val chatManager: ChatManager
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }

    private val appContext = context
    private val appSettings = AppSettingsDataStore(context)
    private val openClawSettingsStore = OpenClawLocalSettingsStore(context)
    private val airLlmGatewayClient = AirLlmGatewayClient()
    private val officialOpenClawGatewayClient = OfficialOpenClawGatewayClient()
    private val ttsDataStore = com.santiya.localaihub.tts.TTSDataStore(context)
    private val offlineCityStorage = OfflineCityStorage(context)
    private val offlineCityAssistant = OfflineCityAssistant()
    private val offlineCityLocationProvider = OfflineCityLocationProvider(context)
    // ControlVectorManager removed РІР‚вЂќ will be re-added when new lib supports it

    val streamingEnabled: StateFlow<Boolean> = appSettings.streamingEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val chatMemoryEnabled: StateFlow<Boolean> = appSettings.chatMemoryEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val activeModelProvider: StateFlow<ProviderType?> = appSettings.activeModelState
        .map { state ->
            state.providerTypeName?.let { runCatching { ProviderType.valueOf(it) }.getOrNull() }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val activeAirLlmModelId: StateFlow<String?> = appSettings.activeModelState
        .map { state ->
            state.modelId?.takeIf {
                state.providerTypeName == ProviderType.AIRLLM_REMOTE.name &&
                    state.activationState == ActiveModelActivationState.ACTIVE
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun officialGatewaySettingsOrNull() = runCatching {
        openClawSettingsStore.read().takeIf { settings ->
            (settings.defaultBackend == LocalBackendOption.OFFICIAL_GATEWAY ||
                settings.defaultBackend == LocalBackendOption.TERMUX_LOCAL) &&
                (_openClawEnabled.value || settings.enabledByDefault)
        }
    }.getOrNull()

    private val _messages = mutableStateListOf<Messages>()
    val messages: SnapshotStateList<Messages> = _messages

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId

    private val isNewConversation: Boolean get() = _currentChatId.value == null

    // Streaming state
    private val _streamingUserMessage = MutableStateFlow<String?>(null)
    val streamingUserMessage: StateFlow<String?> = _streamingUserMessage

    private val _streamingAssistantMessage = MutableStateFlow("")
    val streamingAssistantMessage: StateFlow<String> = _streamingAssistantMessage

    // Image generation state
    private val _streamingImage = MutableStateFlow<Bitmap?>(null)
    val streamingImage: StateFlow<Bitmap?> = _streamingImage

    private val _imageGenerationProgress = MutableStateFlow(0f)
    val imageGenerationProgress: StateFlow<Float> = _imageGenerationProgress

    private val _imageGenerationStep = MutableStateFlow("")
    val imageGenerationStep: StateFlow<String> = _imageGenerationStep

    // Tool chain steps (used by both streaming UI and persistence)
    private val _toolChainSteps = MutableStateFlow<List<ToolChainStepData>>(emptyList())
    val toolChainSteps: StateFlow<List<ToolChainStepData>> = _toolChainSteps

    private val _currentToolChainRound = MutableStateFlow(0)
    val currentToolChainRound: StateFlow<Int> = _currentToolChainRound

    // Agent phase state (Plan РІвЂ вЂ™ Execute РІвЂ вЂ™ Summarize)
    private val _agentPhase = MutableStateFlow(AgentPhase.Idle)
    val agentPhase: StateFlow<AgentPhase> = _agentPhase.asStateFlow()

    private val _agentPlan = MutableStateFlow<String?>(null)
    val agentPlan: StateFlow<String?> = _agentPlan.asStateFlow()

    private val _agentSummary = MutableStateFlow<String?>(null)
    val agentSummary: StateFlow<String?> = _agentSummary.asStateFlow()

    // Track generation job for proper cancellation
    private var generationJob: Job? = null

    // Track current generation state
    private var currentUserMessage: Messages? = null
    private val _currentMetrics = MutableStateFlow<DecodingMetrics?>(null)
    val currentDecodingMetrics: StateFlow<DecodingMetrics?> = _currentMetrics.asStateFlow()
    private var currentMetrics: DecodingMetrics?
        get() = _currentMetrics.value
        set(value) { _currentMetrics.value = value }
    private var currentImageMetrics: ImageGenerationMetrics? = null
    private var currentGeneratedImage: Bitmap? = null
    private var imageGenerationStartTime: Long = 0

    // Track if user message was already added to prevent duplicates
    private val userMessageAdded = java.util.concurrent.atomic.AtomicBoolean(false)

    // Current model ID for per-message attribution
    private val currentModelId: String?
        get() = LlmModelWorker.currentGoogleLocalModelId.value
            ?: LlmModelWorker.currentGgufModelId.value
            ?: activeAirLlmModelId.value
            ?: officialGatewaySettingsOrNull()?.officialGatewayModelId?.ifBlank { OfficialOpenClawGatewayClient.DEFAULT_MODEL_ID }

    /** True when a text generation model is loaded. */
    private val isAnyTextModelLoaded: Boolean
        get() = LlmModelWorker.isGgufModelLoaded.value ||
            LlmModelWorker.isGoogleLocalModelLoaded.value ||
            activeAirLlmModelId.value != null ||
            officialGatewaySettingsOrNull() != null

    private val currentTextProviderType: ProviderType?
        get() = when {
            LlmModelWorker.isGoogleLocalModelLoaded.value -> ProviderType.GOOGLE_LOCAL
            LlmModelWorker.isGgufModelLoaded.value -> ProviderType.GGUF
            activeAirLlmModelId.value != null -> ProviderType.AIRLLM_REMOTE
            else -> null
        }

    // UI state
    private val _showDynamicWindow = MutableStateFlow(false)
    val showDynamicWindow: StateFlow<Boolean> = _showDynamicWindow

    private val _showModelList = MutableStateFlow(false)
    val showModelList: StateFlow<Boolean> = _showModelList

    private val _currentGenerationType = MutableStateFlow(ModelType.TEXT_GENERATION)
    val currentGenerationType: StateFlow<ModelType> = _currentGenerationType

    // Thinking mode toggle РІР‚вЂќ when enabled, adds /think to system prompt for supported models
    private val _thinkingModeEnabled = MutableStateFlow(false)
    val thinkingModeEnabled: StateFlow<Boolean> = _thinkingModeEnabled.asStateFlow()
    private val _openClawEnabled = MutableStateFlow(false)
    val openClawEnabled: StateFlow<Boolean> = _openClawEnabled.asStateFlow()
    private val _openClawMode = MutableStateFlow(OpenClawMode.NORMAL)
    val openClawMode: StateFlow<OpenClawMode> = _openClawMode.asStateFlow()
    private val _modelSupportsThinking = MutableStateFlow(false)
    val modelSupportsThinking: StateFlow<Boolean> = _modelSupportsThinking.asStateFlow()

    data class OpenClawUiFlags(
        val generationType: ModelType,
        val openClawEnabled: Boolean,
        val thinkingEnabled: Boolean,
        val modelSupportsThinking: Boolean,
        val openClawMode: OpenClawMode,
    )

    fun toggleThinkingMode() {
        setThinkingMode(!_thinkingModeEnabled.value)
    }

    fun setThinkingMode(enabled: Boolean) {
        _thinkingModeEnabled.value = enabled
        _openClawMode.value = if (enabled) OpenClawMode.THINKING else OpenClawMode.NORMAL
    }

    fun setOpenClawMode(mode: OpenClawMode) {
        _openClawMode.value = mode
        _thinkingModeEnabled.value = mode == OpenClawMode.THINKING
        persistOpenClawSessionState()
    }

    fun setOpenClawEnabled(enabled: Boolean) {
        _openClawEnabled.value = enabled
        syncOpenClawToolsFromSettings(enabled)
        persistOpenClawSessionState()
    }

    fun toggleOpenClawEnabled() {
        setOpenClawEnabled(!_openClawEnabled.value)
    }

    // Model state
    val isTextModelLoaded: StateFlow<Boolean> = combine(
        LlmModelWorker.isGgufModelLoaded,
        LlmModelWorker.isGoogleLocalModelLoaded
    ) { ggufLoaded, googleLoaded ->
        ggufLoaded || googleLoaded
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val isImageModelLoaded = LlmModelWorker.isDiffusionModelLoaded
    val isVlmLoaded = LlmModelWorker.isVlmLoaded

    // TTS state
    val ttsPlayingMsgId = TTSManager.currentPlayingMsgId
    val ttsIsPlaying = TTSManager.isPlaying
    val ttsSynthesizing = TTSManager.isSynthesizing
    val ttsModelLoaded = TTSManager.isModelLoaded
    val ttsAvailableVoices = TTSManager.availableVoices

    // RAG state
    private val _currentRagContext = MutableStateFlow<String?>(null)
    val currentRagContext: StateFlow<String?> = _currentRagContext

    private val _currentRagResults = MutableStateFlow<List<RagQueryDisplayResult>>(emptyList())
    val currentRagResults: StateFlow<List<RagQueryDisplayResult>> = _currentRagResults

    private val _offlineCityAnswer = MutableStateFlow<OfflineCityAnswer?>(null)
    val offlineCityAnswer: StateFlow<OfflineCityAnswer?> = _offlineCityAnswer.asStateFlow()

    // РІвЂќР‚РІвЂќР‚ Context Usage РІвЂќР‚РІвЂќР‚

    private val _contextUsagePercent = MutableStateFlow(0f)
    val contextUsagePercent: StateFlow<Float> = _contextUsagePercent.asStateFlow()

    // РІвЂќР‚РІвЂќР‚ Grouped State Flows (for optimized recomposition) РІвЂќР‚РІвЂќР‚

    val streamingState: StateFlow<StreamingState> = combine(
        _streamingUserMessage,
        _streamingAssistantMessage,
        _streamingImage,
        _imageGenerationProgress,
        _imageGenerationStep
    ) { userMsg, assistantMsg, image, progress, step ->
        StreamingState(userMsg, assistantMsg, image, progress, step)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreamingState())

    val chatUiState: StateFlow<ChatUiState> = combine(
        combine(_isGenerating, _currentChatId, _error) { gen, chatId, err -> Triple(gen, chatId, err) },
        combine(_currentGenerationType, _openClawEnabled, _thinkingModeEnabled, _modelSupportsThinking, _openClawMode) { type, enabled, think, supports, mode ->
            OpenClawUiFlags(type, enabled, think, supports, mode)
        }
    ) { (gen, chatId, err), flags ->
        ChatUiState(
            isGenerating = gen,
            currentChatId = chatId,
            error = err,
            generationType = flags.generationType,
            openClawEnabled = flags.openClawEnabled,
            thinkingEnabled = flags.thinkingEnabled,
            modelSupportsThinking = flags.modelSupportsThinking,
            openClawMode = flags.openClawMode
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    val agentState: StateFlow<AgentState> = combine(
        _agentPhase,
        _agentPlan,
        _agentSummary,
        _toolChainSteps,
        _currentToolChainRound
    ) { phase, plan, summary, steps, round ->
        AgentState(phase, plan, summary, steps, round)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AgentState())

    val ragState: StateFlow<RagState> = combine(
        _currentRagContext,
        _currentRagResults
    ) { context, results ->
        RagState(context, results)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RagState())

    val chatConfigState: StateFlow<ChatConfigState> = combine(
        streamingEnabled,
        chatMemoryEnabled,
        _showDynamicWindow,
        _showModelList
    ) { streaming, memory, dynWindow, modelList ->
        ChatConfigState(streaming, memory, dynWindow, modelList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatConfigState())

    // ==================== Auto-restore last chat ====================

    init {
        runCatching {
            val settings = openClawSettingsStore.read()
            _openClawEnabled.value = if (settings.lastSessionEnabled) true else settings.enabledByDefault
            _openClawMode.value = settings.lastSessionMode
            _thinkingModeEnabled.value = settings.lastSessionMode == OpenClawMode.THINKING
            syncOpenClawToolsFromSettings(_openClawEnabled.value)
            restorePersistedOpenClawRuntime(settings)
        }

        viewModelScope.launch {
            try {
                val lastChatId = openClawSettingsStore.read().lastSessionChatId ?: appSettings.lastChatId.first()
                if (lastChatId != null) {
                    chatManager.getChatMessages(lastChatId).onSuccess { loadedMessages ->
                        if (loadedMessages.isNotEmpty()) {
                            _currentChatId.value = lastChatId
                            _messages.clear()
                            _messages.addAll(loadedMessages)
                            restoreAgentSessionFromMessages(loadedMessages)
                            AppStateManager.setHasMessages(true)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Could not restore last chat: ${e.message}")
            }
        }

        // Persist chat ID whenever it changes
        viewModelScope.launch {
            try {
                _currentChatId.collect { chatId ->
                    appSettings.saveLastChatId(chatId)
                    persistOpenClawSessionState()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Chat ID persistence failed: ${e.message}")
            }
        }

        viewModelScope.launch {
            BrowserToolState.state.collect {
                persistOpenClawSessionState()
            }
        }

        // Check thinking support whenever text model loads/unloads
        viewModelScope.launch {
            combine(
                LlmModelWorker.isGgufModelLoaded,
                LlmModelWorker.isGoogleLocalModelLoaded
            ) { ggufLoaded, googleLoaded ->
                ggufLoaded to googleLoaded
            }.collect { (ggufLoaded, googleLoaded) ->
                if (ggufLoaded) {
                    val supports = LlmModelWorker.supportsThinkingGguf()
                    _modelSupportsThinking.value = supports
                    if (!supports && _openClawMode.value == OpenClawMode.THINKING) {
                        _thinkingModeEnabled.value = false
                        _openClawMode.value = OpenClawMode.NORMAL
                    }
                } else {
                    _modelSupportsThinking.value = false
                    if (googleLoaded || _openClawMode.value == OpenClawMode.THINKING) {
                        _thinkingModeEnabled.value = false
                        _openClawMode.value = OpenClawMode.NORMAL
                    }
                }
            }
        }
    }

    private fun persistOpenClawSessionState() {
        runCatching {
            val current = openClawSettingsStore.read()
            openClawSettingsStore.write(
                current.copy(
                    lastSessionEnabled = _openClawEnabled.value,
                    lastSessionMode = _openClawMode.value,
                    lastSessionChatId = _currentChatId.value,
                    lastAgentPlan = _agentPlan.value,
                    lastAgentSummary = _agentSummary.value,
                    lastToolChainStepsJson = _toolChainSteps.value.takeIf { it.isNotEmpty() }?.let(json::encodeToString),
                    lastBrowserUrl = BrowserToolState.state.value.currentUrl,
                    lastBrowserTitle = BrowserToolState.state.value.pageTitle,
                    lastOfflineCityAnswerJson = _offlineCityAnswer.value?.let(json::encodeToString),
                )
            )
        }
    }

    private fun restorePersistedOpenClawRuntime(settings: com.santiya.localaihub.hub.OpenClawLocalSettings) {
        runCatching {
            BrowserToolState.restore(
                BrowserSessionSnapshot(
                    currentUrl = settings.lastBrowserUrl,
                    pageTitle = settings.lastBrowserTitle,
                ).takeIf { !it.currentUrl.isNullOrBlank() || !it.pageTitle.isNullOrBlank() }
            )
            _offlineCityAnswer.value = settings.lastOfflineCityAnswerJson
                ?.takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString<OfflineCityAnswer>(it) }
            _agentPlan.value = settings.lastAgentPlan
            _agentSummary.value = settings.lastAgentSummary
            _toolChainSteps.value = settings.lastToolChainStepsJson
                ?.takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString<List<ToolChainStepData>>(it) }
                .orEmpty()
            _currentToolChainRound.value = _toolChainSteps.value.maxOfOrNull { it.round } ?: 0
            _agentPhase.value = when {
                _agentSummary.value != null -> AgentPhase.Complete
                _agentPlan.value != null || _toolChainSteps.value.isNotEmpty() -> AgentPhase.Executing
                else -> AgentPhase.Idle
            }
        }
    }

    private fun restoreAgentSessionFromMessages(loadedMessages: List<Messages>) {
        val lastAssistant = loadedMessages.lastOrNull { it.role == Role.Assistant }
        _agentPlan.value = lastAssistant?.agentPlan
        _agentSummary.value = lastAssistant?.agentSummary
        _toolChainSteps.value = lastAssistant?.toolChainSteps.orEmpty()
        _currentToolChainRound.value = _toolChainSteps.value.maxOfOrNull { it.round } ?: 0
        _agentPhase.value = when {
            lastAssistant?.agentSummary != null -> AgentPhase.Complete
            lastAssistant?.agentPlan != null || !lastAssistant?.toolChainSteps.isNullOrEmpty() -> AgentPhase.Executing
            else -> AgentPhase.Idle
        }
        persistOpenClawSessionState()
    }

    private fun syncOpenClawToolsFromSettings(enabled: Boolean = _openClawEnabled.value) {
        val settings = runCatching { openClawSettingsStore.read() }.getOrNull() ?: return
        val selectedSkills = settings.selectedSkillIds
            .takeIf { it.isNotEmpty() }
            ?.toSet()
            ?: setOf("hermes", "travel_offline", "browser", "files", "memory", "automation", "location_control", "airllm", "official_openclaw_gateway")
        val selectedTools = settings.selectedApiToolIds
            .takeIf { it.isNotEmpty() }
            ?.toSet()
            ?: setOf("hermes", "web_search", "browser", "api_models", "support_logs", "system_info", "location_control", "airllm", "official_openclaw_gateway")
        val effectiveSkills = selectedSkills + setOf("files", "memory", "automation", "hermes")
        val effectiveTools = selectedTools + setOf(
            "hermes",
            "web_search",
            "browser",
            "system_info",
            "location_control",
            "calculator",
            "date_time",
            "airllm",
            "official_openclaw_gateway"
        )
        val enableTermux = enabled &&
            (settings.defaultBackend == LocalBackendOption.TERMUX_LOCAL ||
                "termux" in selectedSkills ||
                "termux" in selectedTools)
        val enableAirLlm = enabled &&
            (settings.defaultBackend == LocalBackendOption.AIRLLM_REMOTE ||
                "airllm" in selectedSkills ||
                "airllm" in selectedTools)
        val enableOfficialGateway = enabled &&
            (settings.defaultBackend == LocalBackendOption.OFFICIAL_GATEWAY ||
                settings.defaultBackend == LocalBackendOption.TERMUX_LOCAL ||
                "official_openclaw_gateway" in selectedSkills ||
                "official_openclaw_gateway" in selectedTools)

        PluginManager.enableWebSearch(enabled && "web_search" in effectiveTools)
        PluginManager.togglePlugin("Hermes Agent", enabled && ("hermes" in effectiveTools || "hermes" in effectiveSkills))
        PluginManager.togglePlugin("Browser", enabled && "browser" in effectiveTools)
        PluginManager.togglePlugin("File Manager", enabled && "files" in effectiveSkills)
        PluginManager.togglePlugin("NotePad", enabled && "memory" in effectiveSkills)
        PluginManager.togglePlugin("Automation", enabled && "automation" in effectiveSkills)
        PluginManager.togglePlugin("Termux", enableTermux)
        PluginManager.togglePlugin("AirLLM Gateway", enableAirLlm)
        PluginManager.togglePlugin("Official OpenClaw Gateway", enableOfficialGateway)
        PluginManager.togglePlugin("System Info", enabled && "system_info" in effectiveTools)
        PluginManager.togglePlugin("Location Control", enabled && "location_control" in effectiveTools)
        PluginManager.togglePlugin("Calculator", enabled && "calculator" in effectiveTools)
        PluginManager.togglePlugin("Date & Time", enabled && "date_time" in effectiveTools)
    }

    private fun currentOpenClawBackend(): LocalBackendOption {
        return runCatching { openClawSettingsStore.read().defaultBackend }
            .getOrDefault(LocalBackendOption.GGUF_LOCAL)
    }

    private fun allowsGoogleLocalOpenClawTools(): Boolean {
        return true
    }

    // ==================== RAG Controls ====================

    fun setRagContext(context: String?, results: List<RagQueryDisplayResult> = emptyList()) {
        _currentRagContext.value = context
        _currentRagResults.value = results
    }

    fun clearRagContext() {
        _currentRagContext.value = null
        _currentRagResults.value = emptyList()
    }

    /** Fetch the current GGUF model's inference config from DB (cached per call). */
    private suspend fun getGgufModelSchema(): GgufEngineSchema {
        val modelId = LlmModelWorker.currentGgufModelId.value ?: return GgufEngineSchema()
        val config = AppContainer.getModelRepository().getConfigByModelId(modelId) ?: return GgufEngineSchema()
        return GgufEngineSchema.fromJson(config.modelLoadingParams, config.modelInferenceParams)
    }

    private suspend fun getModelInferenceParams(): GgufInferenceParams =
        getGgufModelSchema().inferenceParams

    // ==================== Chat Management ====================

    fun startNewConversation() {
        // Cancel any in-flight generation before switching
        generationJob?.cancel()
        generationJob = null

        _currentChatId.value = null
        _messages.clear()
        _streamingUserMessage.value = null
        _streamingAssistantMessage.value = ""
        _streamingImage.value = null
        _imageGenerationProgress.value = 0f
        _imageGenerationStep.value = ""
        _isGenerating.value = false
        currentUserMessage = null
        currentGeneratedImage = null
        currentMetrics = null
        currentImageMetrics = null
        userMessageAdded.set(false)
        _error.value = null
        _toolChainSteps.value = emptyList()
        _currentToolChainRound.value = 0
        _agentPhase.value = AgentPhase.Idle
        _agentPlan.value = null
        _agentSummary.value = null
        _currentRagContext.value = null
        _currentRagResults.value = emptyList()
        _offlineCityAnswer.value = null
        AppStateManager.setHasMessages(false)
        persistOpenClawSessionState()
    }

    fun loadChat(chatId: String) {
        viewModelScope.launch {
            try {
                _offlineCityAnswer.value = null
                persistOpenClawSessionState()
                _currentChatId.value = chatId
                chatManager.getChatMessages(chatId).onSuccess { loadedMessages ->
                    _messages.clear()
                    _messages.addAll(loadedMessages)
                    restoreAgentSessionFromMessages(loadedMessages)
                    AppStateManager.setHasMessages(loadedMessages.isNotEmpty())
                }.onFailure { e ->
                    reportError("Failed to load chat: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load chat: ${e.message}")
                reportError("Failed to load chat: ${e.message}")
            }
        }
    }

    // ==================== Model Selection ====================

    fun switchToTextGeneration() {
        if (!isAnyTextModelLoaded) {
            _error.value = "Text generation model not loaded"
            return
        }
        _currentGenerationType.value = ModelType.TEXT_GENERATION
    }

    fun switchToImageGeneration() {
        if (!LlmModelWorker.isDiffusionModelLoaded.value) {
            _error.value = "Image generation model not loaded"
            return
        }
        _currentGenerationType.value = ModelType.IMAGE_GENERATION
    }

    // ==================== Unified Text Generation Entry Point ====================

    fun sendChat(prompt: String) {
        maybeBuildOfflineCityAnswer(prompt)?.let { answer ->
            sendOfflineCityChat(prompt, answer)
            return
        }
        _offlineCityAnswer.value = null
        persistOpenClawSessionState()
        if (!isAnyTextModelLoaded) {
            runCatching { kotlinx.coroutines.runBlocking { recoverPersistedTextModelIfNeeded() } }
                .onFailure { Log.w(TAG, "Text-model recovery failed before send: ${it.message}") }
        }
        if (!isAnyTextModelLoaded) {
            val hint = if (LlmModelWorker.isDiffusionModelLoaded.value)
                "You have an image model loaded РІР‚вЂќ switch to image mode, or load a text model for chat"
            else
                "Please load a text generation model first"
            reportError(hint)
            return
        }
        if (_isGenerating.value) {
            stop()
        }

        _isGenerating.value = true
        _streamingUserMessage.value = prompt
        _streamingAssistantMessage.value = ""
        userMessageAdded.set(false)
        currentMetrics = null
        _error.value = null

        currentUserMessage = Messages(
            msgId = "",
            role = Role.User,
            content = MessageContent(contentType = ContentType.Text, content = prompt),
            modelId = currentModelId,
        )
        AppStateManager.setHasMessages(true)

        generationJob = viewModelScope.launch {
            try {
                Log.d(TAG, "sendChat provider=$currentTextProviderType openClaw=${_openClawEnabled.value} promptLen=${prompt.length}")
                // Let Compose render the StreamingView before native engine saturates CPU
                kotlinx.coroutines.yield()

                // Read maxTokens from the current model's config
                val maxTokens = getCurrentModelMaxTokens()

                val isNewChat = isNewConversation
                syncOpenClawToolsFromSettings(_openClawEnabled.value)
                val hasTools = PluginManager.hasEnabledTools()
                        && PluginManager.isToolCallingModelLoaded.value
                val selectedMode = if (_openClawEnabled.value) _openClawMode.value else OpenClawMode.NORMAL
                val useOpenClawAgent = _openClawEnabled.value
                val useOrchestra = selectedMode == OpenClawMode.ORCHESTRA
                val useThinking = selectedMode == OpenClawMode.THINKING
                val usingOfficialGateway = officialGatewaySettingsOrNull() != null
                if (currentTextProviderType == ProviderType.GOOGLE_LOCAL && (useThinking || useOrchestra)) {
                    reportError(
                        "Google Local поддерживает обычный OpenClaw agent mode с Hermes tools. Думающий режим и оркестр оставьте на GGUF/Termux."
                    )
                    return@launch
                }
                if (useOrchestra && !hasTools && !usingOfficialGateway) {
                    reportError("OpenClaw Orchestra requires local tools and a loaded local tool-capable model.")
                    return@launch
                }
                LlmModelWorker.setThinkingEnabledGguf(useThinking && !hasTools)
                val ragContext = _currentRagContext.value

                // For existing chats, save user message upfront
                if (!isNewChat) {
                    val chatId = _currentChatId.value ?: run {
                        reportError("No chat selected")
                        return@launch  // finally will call resetStreamingState()
                    }
                    chatManager.addUserMessage(chatId, prompt).onSuccess { userMsg ->
                        currentUserMessage = userMsg
                    }.onFailure { e ->
                        reportError("Failed to save message: ${e.message}")
                        return@launch  // finally will call resetStreamingState()
                    }
                }

                if (useOpenClawAgent) {
                    agentFlow(prompt, ragContext, maxTokens, isNewChat)
                } else {
                    simpleFlow(prompt, ragContext, maxTokens, isNewChat)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d(TAG, "sendChat cancelled cleanly")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error in sendChat", e)
                reportError(e.message)
            } finally {
                resetStreamingState()
            }
        }
    }

    // Keep old name as alias for backward compatibility with callers
    fun sendTextMessage(prompt: String) = sendChat(prompt)

    private suspend fun recoverPersistedTextModelIfNeeded() {
        if (isAnyTextModelLoaded) return
        val activeState = runCatching { appSettings.activeModelStateSnapshot() }.getOrNull() ?: return
        if (!activeState.hasSelection || activeState.activationState != ActiveModelActivationState.ACTIVE) return

        val modelId = activeState.modelId ?: return
        val repository = AppContainer.getModelRepository()
        val model = runCatching { repository.getModelById(modelId) }.getOrNull() ?: return

        when {
            activeState.providerTypeName == ProviderType.AIRLLM_REMOTE.name -> {
                PluginManager.togglePlugin("AirLLM Gateway", true)
            }
            activeState.providerTypeName == ProviderType.GOOGLE_LOCAL.name ||
                GoogleLocalSupport.shouldPreferForGemma(appContext, model.id, model.modelName) -> {
                val descriptor = GoogleLocalSupport.buildGemmaDescriptor(model.id, model.modelName)
                val success = kotlinx.coroutines.runBlocking {
                    LlmModelWorker.loadGoogleLocalModel(appContext, descriptor)
                }
                if (success) {
                    LlmModelWorker.setCurrentGoogleLocalModelId(model.id)
                    LlmModelWorker.setCurrentGgufModelId(null)
                }
            }
            else -> {
                val config = runCatching { repository.getConfigByModelId(modelId) }.getOrNull() ?: return
                val success = kotlinx.coroutines.runBlocking {
                    LlmModelWorker.loadGgufModel(model, config)
                }
                if (success) {
                    LlmModelWorker.setCurrentGgufModelId(model.id)
                }
            }
        }
    }

    private fun maybeBuildOfflineCityAnswer(prompt: String): OfflineCityAnswer? {
        if (!OfflineCityAssistant.looksLikeTravelQuery(prompt)) return null
        val dataset = offlineCityStorage.loadDataset() ?: return null
        val language = com.santiya.localaihub.global.AppLanguageManager.readPersistedLanguage(appContext)
        val location = runCatching { offlineCityLocationProvider.readLastKnownLocation() }.getOrNull()
        return offlineCityAssistant.answer(dataset, prompt, language, location)
    }

    private fun sendOfflineCityChat(prompt: String, answer: OfflineCityAnswer) {
        if (_isGenerating.value) return
        _offlineCityAnswer.value = answer
        persistOpenClawSessionState()
        _isGenerating.value = true
        _streamingUserMessage.value = prompt
        _streamingAssistantMessage.value = answer.body
        userMessageAdded.set(false)
        currentMetrics = null
        _error.value = null
        currentUserMessage = Messages(
            msgId = "",
            role = Role.User,
            content = MessageContent(contentType = ContentType.Text, content = prompt),
            modelId = currentModelId,
        )
        AppStateManager.setHasMessages(true)
        AppStateManager.setGeneratingText()

        generationJob = viewModelScope.launch {
            try {
                val assistantText = buildString {
                    append(answer.title)
                    append("\n")
                    append(answer.body)
                }
                if (isNewConversation) {
                    createChatWithMessages(prompt, assistantText, null)
                } else {
                    val chatId = _currentChatId.value ?: return@launch
                    chatManager.addUserMessage(chatId, prompt).onSuccess { userMsg ->
                        currentUserMessage = userMsg
                    }.onFailure { e ->
                        reportError("Failed to save message: ${e.message}")
                        return@launch
                    }
                    val pendingUserMsg = currentUserMessage
                    if (!userMessageAdded.get() && pendingUserMsg != null) {
                        _messages.add(pendingUserMsg)
                        userMessageAdded.set(true)
                    }
                    val assistantMessage = Messages(
                        role = Role.Assistant,
                        content = MessageContent(contentType = ContentType.Text, content = assistantText),
                        modelId = currentModelId,
                    )
                    _messages.add(assistantMessage)
                    chatManager.addMessage(chatId, assistantMessage)
                    AppStateManager.setGenerationComplete()
                    AppStateManager.chatRefreshed()
                    viewModelScope.launch { autoSpeakIfEnabled(assistantText, assistantMessage.msgId) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in offline city sendChat", e)
                reportError(e.message)
            } finally {
                resetStreamingState()
            }
        }
    }

    /**
     * Send a message with images (VLM). Requires a VLM projector to be loaded.
     * @param prompt User's text prompt
     * @param imageData List of raw image file bytes (JPEG/PNG)
     */
    fun sendChatWithImages(prompt: String, imageData: List<ByteArray>) {
        if (!isAnyTextModelLoaded) {
            reportError("Please load a text generation model first")
            return
        }
        if (!LlmModelWorker.isVlmLoaded.value) {
            reportError("Please load a vision projector (mmproj) first")
            return
        }
        if (_isGenerating.value) return

        _isGenerating.value = true
        _streamingUserMessage.value = prompt
        _streamingAssistantMessage.value = ""
        userMessageAdded.set(false)
        currentMetrics = null
        _error.value = null

        currentUserMessage = Messages(
            msgId = "",
            role = Role.User,
            content = MessageContent(contentType = ContentType.Text, content = prompt),
            modelId = currentModelId,
        )
        AppStateManager.setHasMessages(true)

        generationJob = viewModelScope.launch {
            try {
                // Let Compose render the StreamingView before native engine saturates CPU
                kotlinx.coroutines.yield()

                val maxTokens = getCurrentModelMaxTokens()
                val isNewChat = isNewConversation

                // Insert image marker into prompt for VLM
                val marker = LlmModelWorker.getVlmDefaultMarker()
                val vlmPrompt = if (prompt.contains(marker)) prompt
                    else marker.repeat(imageData.size) + "\n" + prompt

                val conversationMessages = buildConversationMessages(vlmPrompt)
                val jsonArray = JSONArray(conversationMessages)

                AppStateManager.setGeneratingText()

                val resultBuilder = StringBuilder()
                var lastEmitTime = 0L

                LlmModelWorker.vlmGenerateStreaming(
                    jsonArray.toString(), imageData, maxTokens
                ).collect { event ->
                    when (event) {
                        is GenerationEvent.Token -> {
                            resultBuilder.append(event.text)
                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime >= STREAMING_THROTTLE_MS) {
                                _streamingAssistantMessage.value = resultBuilder.toString()
                                lastEmitTime = now
                            }
                        }
                        is GenerationEvent.Done -> {
                            _streamingAssistantMessage.value = resultBuilder.toString()
                        }
                        is GenerationEvent.Metrics -> { currentMetrics = event.metrics }
                        is GenerationEvent.Progress -> { /* progress tracked elsewhere */ }
                        is GenerationEvent.Error -> {
                            Log.e(TAG, "VLM generation error: ${event.message}")
                            throw Exception(event.message)
                        }
                        is GenerationEvent.ToolCall -> { /* VLM doesn't support tool calling */ }
                    }
                }

                val finalResponse = resultBuilder.toString()
                _streamingAssistantMessage.value = finalResponse

                if (isNewChat) {
                    createChatWithMessages(prompt, finalResponse, currentMetrics)
                } else {
                    val chatId = _currentChatId.value ?: return@launch
                    val pendingUserMsg = currentUserMessage
                    if (!userMessageAdded.get() && pendingUserMsg != null) {
                        _messages.add(pendingUserMsg)
                        userMessageAdded.set(true)
                    }
                    if (finalResponse.isNotBlank()) {
                        val assistantMessage = Messages(
                            role = Role.Assistant,
                            content = MessageContent(contentType = ContentType.Text, content = finalResponse),
                            modelId = currentModelId,
                            decodingMetrics = currentMetrics,
                        )
                        _messages.add(assistantMessage)
                        chatManager.addMessage(chatId, assistantMessage)
                        AppStateManager.setGenerationComplete()
                        AppStateManager.chatRefreshed()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error in sendChatWithImages", e)
                reportError(e.message)
            } finally {
                resetStreamingState()
            }
        }
    }

    /**
     * Regenerate the last assistant response.
     * Removes the last assistant message and re-sends the last user prompt.
     */
    // Snapshot of old assistant message during regeneration РІР‚вЂќ restored if stop() is
    // called before new content arrives (fixes issue #77: message disappears on cancel)
    private var regenerationSnapshot: Messages? = null

    fun regenerateLastMessage() {
        if (!isAnyTextModelLoaded) {
            _error.value = "Please load a text generation model first"
            return
        }
        if (_isGenerating.value) return

        val chatId = _currentChatId.value ?: return

        // Find the last user message to get the prompt
        val lastUserMsg = _messages.lastOrNull { it.role == Role.User }
        if (lastUserMsg == null) {
            _error.value = "No user message to regenerate from"
            return
        }

        // Snapshot the old assistant message РІР‚вЂќ remove from UI but keep for rollback
        val lastAssistantMsg = _messages.lastOrNull { it.role == Role.Assistant }
        regenerationSnapshot = lastAssistantMsg
        if (lastAssistantMsg != null) {
            _messages.remove(lastAssistantMsg)
        }

        val prompt = lastUserMsg.content.content

        // Set up generation state without creating a new user message
        _isGenerating.value = true
        _streamingUserMessage.value = prompt
        _streamingAssistantMessage.value = ""
        currentUserMessage = lastUserMsg // needed for stop() rollback
        userMessageAdded.set(true) // already added РІР‚вЂќ skip re-adding user message
        currentMetrics = null
        _error.value = null

        generationJob = viewModelScope.launch {
            try {
                val maxTokens = getCurrentModelMaxTokens()
                syncOpenClawToolsFromSettings(_openClawEnabled.value)
                val hasTools = PluginManager.hasEnabledTools()
                        && PluginManager.isToolCallingModelLoaded.value
                val selectedMode = if (_openClawEnabled.value) _openClawMode.value else OpenClawMode.NORMAL
                val useOpenClawAgent = _openClawEnabled.value
                val useOrchestra = selectedMode == OpenClawMode.ORCHESTRA
                val useThinking = selectedMode == OpenClawMode.THINKING
                if (useOrchestra && !hasTools) {
                    reportError("OpenClaw Orchestra requires local tools and a loaded local tool-capable model.")
                    return@launch
                }
                LlmModelWorker.setThinkingEnabledGguf(useThinking && !hasTools)
                val ragContext = _currentRagContext.value
                if (useOpenClawAgent) {
                    agentFlow(prompt, ragContext, maxTokens, isNewChat = false, isRegeneration = true)
                } else {
                    simpleFlow(prompt, ragContext, maxTokens, isNewChat = false, isRegeneration = true)
                }

                // Generation completed successfully РІР‚вЂќ now delete old message from DB
                if (lastAssistantMsg != null) {
                    chatManager.deleteMessage(lastAssistantMsg.msgId)
                }
                regenerationSnapshot = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                // stop() handles rollback via regenerationSnapshot
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error in regenerateLastMessage", e)
                restoreRegenerationSnapshot()
                reportError(e.message)
            } finally {
                resetStreamingState()
            }
        }
    }

    private fun restoreRegenerationSnapshot() {
        val snapshot = regenerationSnapshot ?: return
        regenerationSnapshot = null
        _messages.add(snapshot)
    }

    private suspend fun getCurrentModelMaxTokens(): Int =
        if (officialGatewaySettingsOrNull() != null) {
            2048
        } else when (currentTextProviderType) {
            ProviderType.GOOGLE_LOCAL -> 2048
            ProviderType.AIRLLM_REMOTE -> 512
            else -> getGgufModelSchema().inferenceParams.maxTokens
        }

    // ==================== Agent Flow (Plan РІвЂ вЂ™ Execute РІвЂ вЂ™ Summarize) ====================

    private suspend fun agentFlow(
        prompt: String,
        ragContext: String?,
        maxTokens: Int,
        isNewChat: Boolean,
        isRegeneration: Boolean = false
    ) {
        val fullPrompt = ragContext?.let { "$it\n\n$prompt" } ?: prompt
        val directToolSequence = buildDirectAgentToolSequence(fullPrompt)

        val plan = if (directToolSequence.isNotEmpty()) {
            directToolSequence.joinToString("\n") { "- ${it.planLine}" }
        } else {
            _agentPhase.value = AgentPhase.Planning
            AppStateManager.setGeneratingText()
            Log.d(TAG, "Agent Phase 1: Generating plan")
            val generatedPlan = generatePlan(fullPrompt)
            _agentPlan.value = generatedPlan
            Log.d(TAG, "Agent plan: $generatedPlan")
            generatedPlan
        }
        _agentPlan.value = plan

        // Phase 2: Bounded generate-execute loop
        _agentPhase.value = AgentPhase.Executing
        _streamingAssistantMessage.value = ""
        Log.d(TAG, "Agent Phase 2: Generate РІвЂ вЂ™ Execute loop")
        val steps = if (directToolSequence.isNotEmpty()) {
            executeDirectToolSequence(directToolSequence)
        } else {
            executeAgentLoop(fullPrompt, plan)
        }
        Log.d(TAG, "Agent execution complete: ${steps.size} steps executed")

        // If no tools were executed or all failed, fall back to simple text generation
        if (steps.isEmpty() || steps.all { !it.success }) {
            _agentPhase.value = AgentPhase.Complete
            persistOpenClawSessionState()
            PluginManager.clearGrammar()
            simpleFlow(prompt, ragContext, maxTokens, isNewChat, isRegeneration)
            return
        }

        // Phase 3: Summary
        _agentPhase.value = AgentPhase.Summarizing
        _streamingAssistantMessage.value = ""
        AppStateManager.setGeneratingText()
        Log.d(TAG, "Agent Phase 3: Generating summary")
        val summary = if (directToolSequence.isNotEmpty()) {
            buildDeterministicAgentSummary(fullPrompt, steps)
        } else {
            generateSummary(fullPrompt, steps)
        }
        _agentSummary.value = summary
        _streamingAssistantMessage.value = summary
        _agentPhase.value = AgentPhase.Complete
        persistOpenClawSessionState()
        Log.d(TAG, "Agent flow complete")

        // Persist
        persistAgentChat(prompt, isNewChat, plan, steps, summary)

    }

    /** Phase 1: Generate a brief plan describing which tools to use. */
    private suspend fun generatePlan(prompt: String): String {
        PluginManager.clearGrammar()
        val russian = isRussianPrompt(prompt)
        val toolDescriptions = PluginManager.getToolDescriptionsText()
        val systemPrompt = buildString {
            appendLine(if (russian) "Доступные инструменты:" else "Available tools:")
            appendLine(toolDescriptions)
            appendLine()
            appendLine(
                if (russian) {
                    "Напиши очень короткий план на русском в 1-2 пункта: какие инструменты вызвать и зачем. Без лишних объяснений."
                } else {
                    "Write a 1-2 sentence plan: which tools to call and what arguments to pass. Be specific and concise."
                }
            )
        }
        val messages = listOf(
            JSONObject().put("role", "system").put("content", systemPrompt),
            JSONObject().put("role", "user").put("content", prompt)
        )
        return withTimeoutOrNull(15_000L) {
            generatePlainText(messages, maxTokens = PLAN_MAX_TOKENS)
        }?.takeIf { it.isNotBlank() } ?: buildFallbackPlan(prompt)
    }

    private fun isRussianPrompt(prompt: String): Boolean =
        prompt.any { it in '\u0400'..'\u04FF' }

    /**
     * Phase 2: Bounded generate РІвЂ вЂ™ execute loop.
     * Each round: generate 1 tool call (grammar-constrained) РІвЂ вЂ™ execute it РІвЂ вЂ™ feed result back.
     * Stops when: no tool call generated, duplicate detected, or max rounds reached.
     */
    private suspend fun executeAgentLoop(
        prompt: String,
        plan: String,
        maxRounds: Int = 5
    ): List<ToolChainStepData> {
        val steps = mutableListOf<ToolChainStepData>()
        val seenCalls = mutableSetOf<String>()
        var consecutiveFailures = 0
        _toolChainSteps.value = emptyList()

        val toolSignatures = PluginManager.getToolSignaturesText()
        val enabledNames = PluginManager.getEnabledToolNames().map { it.lowercase() }
        val truncatedPlan = plan.take(200)

        for (round in 1..maxRounds) {
            // Generate next tool call
            PluginManager.restoreGrammar()
            // Build proper multi-turn messages: system + user + tool call/result pairs
            val messages = mutableListOf<JSONObject>()

            // System prompt always includes tool signatures (model needs param info every round)
            messages.add(JSONObject().put("role", "system").put("content", buildString {
                appendLine("Tools: $toolSignatures")
                if (steps.isEmpty()) appendLine("Plan: $truncatedPlan")
                appendLine("Call the next tool needed, or generate a text response if done.")
            }))

            // Original user request
            messages.add(JSONObject().put("role", "user").put("content", prompt))

            // Previous tool call + result pairs (full context so model sees what already happened)
            for (step in steps) {
                messages.add(JSONObject().put("role", "assistant").put("content",
                    """{"name":"${step.toolName}","arguments":${step.args}}"""
                ))
                messages.add(JSONObject().put("role", "user").put("content",
                    "Tool '${step.toolName}' result: ${step.result}"
                ))
            }
            Log.d(TAG, "Agent loop round $round: generating tool call")
            val toolCalls = generateAndCollectToolCalls(messages, maxTokens = 300)
            if (toolCalls.isEmpty()) {
                Log.d(TAG, "Agent loop round $round: no tool call generated, stopping")
                break
            }

            // Process each tool call from this generation (usually 1)
            var generatedDuplicate = false
            for ((rawName, rawArgs) in toolCalls) {
                val callKey = "${rawName.lowercase()}:${rawArgs.hashCode()}"
                if (callKey in seenCalls) {
                    Log.w(TAG, "Duplicate tool call detected, stopping loop: $rawName")
                    generatedDuplicate = true
                    break
                }
                seenCalls.add(callKey)

                _currentToolChainRound.value = steps.size + 1

                // Parse
                val parsed = extractToolCallFromArgs(rawName, rawArgs)
                if (parsed == null) {
                    Log.e(TAG, "Failed to parse tool call: $rawName")
                    steps.add(ToolChainStepData(
                        round = steps.size + 1,
                        toolName = rawName,
                        pluginName = "Unknown",
                        args = rawArgs.take(500),
                        result = "Failed to parse arguments",
                        executionTimeMs = 0,
                        success = false
                    ))
                    _toolChainSteps.value = steps.toList()
                    consecutiveFailures++
                    if (consecutiveFailures >= 2) {
                        Log.w(TAG, "2 consecutive failures, stopping agent loop")
                        break
                    }
                    continue
                }

                // Execute
                val (toolName, argsObj) = parsed
                val normalizedName = normalizeToolName(toolName)
                repairToolArgumentsFromPrompt(normalizedName, argsObj, prompt)

                // Validate tool name against enabled tools
                if (normalizedName.lowercase() !in enabledNames) {
                    Log.w(TAG, "Hallucinated tool name '$normalizedName', not in enabled tools: $enabledNames")
                    steps.add(ToolChainStepData(
                        round = steps.size + 1,
                        toolName = normalizedName,
                        pluginName = "Unknown",
                        args = rawArgs.take(500),
                        result = "Tool not found: $normalizedName",
                        executionTimeMs = 0,
                        success = false
                    ))
                    _toolChainSteps.value = steps.toList()
                    consecutiveFailures++
                    if (consecutiveFailures >= 2) {
                        Log.w(TAG, "2 consecutive failures, stopping agent loop")
                        break
                    }
                    continue
                }

                if (!isToolCallRelevantToPrompt(normalizedName, argsObj, prompt)) {
                    Log.w(TAG, "Rejected irrelevant tool call '$normalizedName' for prompt: $prompt")
                    steps.add(ToolChainStepData(
                        round = steps.size + 1,
                        toolName = normalizedName,
                        pluginName = "Agent Guard",
                        args = argsObj.toString().take(500),
                        result = "Tool call rejected as unrelated to the current prompt",
                        executionTimeMs = 0,
                        success = false
                    ))
                    _toolChainSteps.value = steps.toList()
                    consecutiveFailures++
                    if (consecutiveFailures >= 2) {
                        Log.w(TAG, "2 consecutive failures, stopping agent loop")
                        break
                    }
                    continue
                }

                AppStateManager.setExecutingPlugin("", normalizedName)

                val toolCall = ToolCall(name = normalizedName, arguments = argsObj)
                val result = PluginManager.executeToolForMultiTurn(toolCall)

                val isSuccess = !result.isError
                if (isSuccess) {
                    consecutiveFailures = 0
                    AppStateManager.setPluginExecutionComplete(
                        pluginName = result.pluginName,
                        toolName = normalizedName,
                        success = true,
                        executionTimeMs = result.executionTimeMs
                    )
                } else {
                    consecutiveFailures++
                    AppStateManager.setPluginExecutionComplete(
                        pluginName = result.pluginName,
                        toolName = normalizedName,
                        success = false,
                        executionTimeMs = result.executionTimeMs,
                        errorMessage = result.resultJson
                    )
                }

                steps.add(ToolChainStepData(
                    round = steps.size + 1,
                    toolName = normalizedName,
                    pluginName = result.pluginName,
                    args = rawArgs.take(2000),
                    result = result.resultJson.take(2000),
                    executionTimeMs = result.executionTimeMs,
                    success = isSuccess
                ))
                _toolChainSteps.value = steps.toList()
                Log.d(TAG, "Agent loop round $round: executed ${normalizedName} (${result.executionTimeMs}ms)")

                if (consecutiveFailures >= 2) {
                    Log.w(TAG, "2 consecutive failures, stopping agent loop")
                    break
                }

                // Add plugin result message for in-memory UI display
                if (result.rawData != null) {
                    val resultData = PluginResultData(
                        pluginName = result.pluginName,
                        toolName = normalizedName,
                        inputParams = argsObj.toString(),
                        resultData = result.resultJson,
                        success = isSuccess
                    )
                    val pluginMessage = Messages(
                        role = Role.Assistant,
                        content = MessageContent(
                            contentType = ContentType.PluginResult,
                            content = "Plugin '${result.pluginName}' executed tool '$normalizedName'",
                            pluginResultData = resultData
                        ),
                        modelId = currentModelId,
                            pluginMetrics = PluginExecutionMetrics(
                            pluginName = result.pluginName,
                            toolName = normalizedName,
                            executionTimeMs = result.executionTimeMs,
                            success = isSuccess
                        )
                    )
                    val pendingUserMsg = currentUserMessage
                    if (!userMessageAdded.get() && pendingUserMsg != null) {
                        _messages.add(pendingUserMsg)
                        userMessageAdded.set(true)
                    }
                    _messages.add(pluginMessage)
                }
            }

            if (generatedDuplicate || consecutiveFailures >= 2) break
        }

        return steps
    }

    private fun repairToolArgumentsFromPrompt(
        normalizedToolName: String,
        argsObj: JSONObject,
        prompt: String
    ) {
        if (normalizedToolName != "browser_open_url") return
        val currentUrl = argsObj.optString("url").trim()
        if (currentUrl.contains('.')) return
        val promptDomain = Regex("""\b([a-zA-Z0-9-]+\.[a-zA-Z0-9.-]+)\b""")
            .find(prompt)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()
        if (promptDomain.isNotBlank()) {
            argsObj.put("url", promptDomain)
        }
    }

    private fun isToolCallRelevantToPrompt(
        normalizedToolName: String,
        argsObj: JSONObject,
        prompt: String
    ): Boolean {
        val normalizedPrompt = prompt.lowercase(Locale.ROOT)
        val wantsTrends = listOf("trend", "trends", "тренд", "тренды").any { normalizedPrompt.contains(it) }
        val wantsWebSearch = listOf("google", "гугл", "web search", "search web", "search", "поиск", "загугл").any {
            normalizedPrompt.contains(it)
        }
        val wantsBrowser = listOf("open", "browser", "брауз", "открой").any { normalizedPrompt.contains(it) }
        val wantsGps = listOf("gps", "location", "геолокац", "локац", "джипиес").any { normalizedPrompt.contains(it) }
        val wantsSettings = listOf("settings", "настройк").any { normalizedPrompt.contains(it) }
        val wantsScript = listOf("script", "скрипт").any { normalizedPrompt.contains(it) }
        val wantsTerminal = listOf("termux", "shell", "bash", "python", "node", "git", "command", "terminal").any {
            normalizedPrompt.contains(it)
        }
        val wantsAirLlm = listOf("airllm", "air llm", "hf model", "hugging face", "больш", "модель больше", "модели больше").any {
            normalizedPrompt.contains(it)
        }
        val wantsOfficialGateway = listOf("official openclaw", "openclaw gateway", "gateway", "официальн", "гейтвей", "шлюз").any {
            normalizedPrompt.contains(it)
        }
        val wantsFileEdit = listOf("file", "files", "файл", "файлы", "write", "save", "создай", "запиши").any {
            normalizedPrompt.contains(it)
        }
        val explicitUrl = Regex("""\b((?:https?://)?[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}(?:/\S*)?)\b""")
            .containsMatchIn(prompt)

        return when (normalizedToolName) {
            "web_search" -> wantsTrends || wantsWebSearch
            "browser_open_url", "hermes_open_browser", "browser_back", "browser_forward", "browser_refresh", "browser_current_page" ->
                wantsBrowser || explicitUrl
            "get_location_status", "hermes_location_status", "set_location_enabled" -> wantsGps
            "open_location_settings", "hermes_open_location_settings" -> wantsGps && wantsSettings
            "execute_script", "hermes_run_script" -> wantsScript || wantsGps || wantsTerminal
            "create_file", "hermes_write_file" -> {
                val path = argsObj.optString("path").lowercase(Locale.ROOT)
                wantsFileEdit || wantsScript || path.contains("automation/")
            }
            "hermes_read_file", "hermes_list_files" -> wantsFileEdit
            "hermes_status" -> normalizedPrompt.contains("hermes") || normalizedPrompt.contains("хермес") ||
                normalizedPrompt.contains("openclaw") || normalizedPrompt.contains("опенклав")
            "termux_status", "termux_exec", "termux_run_workspace_file" -> wantsTerminal || wantsScript
            "airllm_status", "airllm_write_gateway_script" -> wantsAirLlm
            "airllm_generate" -> wantsAirLlm
            "openclaw_gateway_status", "openclaw_gateway_models", "openclaw_gateway_write_setup_scripts",
            "openclaw_gateway_connect_frame", "openclaw_gateway_termux_bootstrap", "openclaw_gateway_termux_start",
            "openclaw_gateway_termux_status", "openclaw_gateway_termux_call" -> wantsOfficialGateway
            "openclaw_gateway_send", "openclaw_gateway_tool_invoke" -> wantsOfficialGateway || normalizedPrompt.contains("openclaw")
            else -> true
        }
    }

    /** Phase 3: Generate a natural language summary from all tool results. */
    private suspend fun generateSummary(
        prompt: String,
        steps: List<ToolChainStepData>
    ): String {
        PluginManager.clearGrammar()
        val resultsText = steps.mapIndexed { i, step ->
            "${i + 1}. ${step.pluginName} (${step.toolName}): ${step.result}"
        }.joinToString("\n")

        val systemPrompt = "You are a helpful assistant. Summarize the tool execution results concisely for the user."
        val userContent = "My request: $prompt\n\nTool Results:\n$resultsText\n\nProvide a helpful summary."

        val messages = listOf(
            JSONObject().put("role", "system").put("content", systemPrompt),
            JSONObject().put("role", "user").put("content", userContent)
        )
        val summary = withTimeoutOrNull(25_000L) {
            generatePlainText(messages, maxTokens = SUMMARY_MAX_TOKENS)
        }?.takeIf { it.isNotBlank() } ?: buildDeterministicAgentSummary(prompt, steps)
        PluginManager.restoreGrammar()  // Re-enable grammar for next message
        return summary
    }

    private data class DirectAgentToolSpec(
        val toolName: String,
        val args: JSONObject,
        val planLine: String,
    )

    private fun buildBuyDipStrategyFileName(prompt: String): String {
        val explicit = Regex("""([A-Za-z0-9_.-]+\.txt)\b""")
            .find(prompt)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        return explicit?.takeIf { it.isNotBlank() } ?: "buy_dip_strategy.txt"
    }

    private fun buildBuyDipStrategyText(prompt: String): String {
        val isRussian = listOf("стратег", "загрузк", "купи", "папк", "бай").any {
            prompt.lowercase(Locale.ROOT).contains(it)
        }
        return if (isRussian) {
            """
            Стратегия buy the dip

            1. Определите базовый актив и максимальный риск на одну идею: не более 1-2% капитала.
            2. Не входите одной покупкой. Разбейте вход на 3 части: первая на откате 5-7%, вторая на 10-12%, третья только после подтверждения удержания уровня.
            3. Покупайте только сильные активы с ликвидностью и понятным новостным фоном, не усредняйте слабые и убыточные позиции без плана.
            4. Перед входом отметьте уровень отмены идеи. Если цена закрывается ниже него, позицию сокращайте, а не усредняйте бесконечно.
            5. Фиксируйте часть прибыли поэтапно: 25% на первом возврате к сопротивлению, остальное переводите в безубыток и ведите по тренду.
            6. Если на рынке сильный новостной риск, сначала дождитесь реакции и только потом набирайте позицию.

            Рабочая схема:
            - список активов с высоким объёмом;
            - уровни входа заранее;
            - частичный вход;
            - жёсткий лимит риска;
            - частичная фиксация прибыли.
            """.trimIndent()
        } else {
            """
            Buy the Dip Strategy

            1. Define the asset and cap risk per idea at 1-2% of total capital.
            2. Never enter in one order. Split entries into three tranches: first on a 5-7% pullback, second near 10-12%, third only after support holds.
            3. Focus on liquid, strong assets with a clear catalyst. Do not average into weak names without a strict invalidation plan.
            4. Mark the invalidation level before entry. If price closes below it, reduce or exit instead of averaging indefinitely.
            5. Scale out gradually: take partial profit on the first move back into resistance, move the rest to breakeven, then trail the trend.
            6. During high-impact news, wait for the first reaction before adding size.
            """.trimIndent()
        }
    }

    private fun buildDirectAgentToolSequence(prompt: String): List<DirectAgentToolSpec> {
        val normalized = prompt.lowercase(Locale.ROOT)
        val russian = isRussianPrompt(prompt)
        val enabledTools = PluginManager.getEnabledToolNames().map { it.lowercase(Locale.ROOT) }.toSet()
        val steps = mutableListOf<DirectAgentToolSpec>()
        fun firstEnabled(vararg names: String): String? = names.firstOrNull { it in enabledTools }

        val wantsTrends = listOf("trend", "trends", "тренд", "тренды").any { normalized.contains(it) }
        val wantsWebSearch = listOf("google", "гугл", "web search", "search web", "загугл").any { normalized.contains(it) }
        if ((wantsTrends || wantsWebSearch) && "web_search" in enabledTools) {
            steps += DirectAgentToolSpec(
                toolName = "web_search",
                args = JSONObject().put("query", prompt).put("max_results", 3),
                planLine = if (russian) {
                    "Найти свежие сигналы по теме через веб-поиск."
                } else {
                    "Search the web for the latest AI trend signals and collect top sources."
                }
            )
        }

        val explicitUrl = Regex("""\b((?:https?://)?[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}(?:/\S*)?)\b""")
            .find(prompt)
            ?.groupValues
            ?.getOrNull(1)
        val wantsBrowser = listOf("open", "browser", "брауз", "открой").any { normalized.contains(it) }
        val openBrowserTool = firstEnabled("hermes_open_browser", "browser_open_url")
        if (!explicitUrl.isNullOrBlank() && wantsBrowser && openBrowserTool != null) {
            steps += DirectAgentToolSpec(
                toolName = openBrowserTool,
                args = JSONObject().put("url", explicitUrl),
                planLine = if (russian) {
                    "Открыть нужную страницу во встроенном браузере."
                } else {
                    "Open the requested page in the embedded browser."
                }
            )
        }

        val wantsHermesStatus = listOf("hermes", "хермес", "openclaw", "опенклав").any { normalized.contains(it) } &&
            listOf("status", "статус", "проверь", "готов", "ready").any { normalized.contains(it) }
        if (wantsHermesStatus && "hermes_status" in enabledTools) {
            steps += DirectAgentToolSpec(
                toolName = "hermes_status",
                args = JSONObject(),
                planLine = if (russian) {
                    "Проверить готовность Hermes/OpenClaw инструментов."
                } else {
                    "Check Hermes/OpenClaw tool readiness."
                }
            )
        }

        val calculatorIntent = listOf("калькулятор", "посчитай", "вычисли", "calculate", "calc").any {
            normalized.contains(it)
        }
        val mathExpression = extractMathExpression(prompt)
        if (calculatorIntent && !mathExpression.isNullOrBlank() && "calculate" in enabledTools) {
            steps += DirectAgentToolSpec(
                toolName = "calculate",
                args = JSONObject().put("expression", mathExpression),
                planLine = if (russian) {
                    "Посчитать выражение через калькулятор."
                } else {
                    "Evaluate the requested expression with the calculator."
                }
            )
        }

        val dateTimeIntent = listOf("время", "дата", "date", "time", "timezone", "час").any {
            normalized.contains(it)
        }
        if (dateTimeIntent && "get_current_datetime" in enabledTools) {
            val timezone = when {
                listOf("москв", "moscow").any { normalized.contains(it) } -> "Europe/Moscow"
                else -> ""
            }
            val format = when {
                listOf("только время", "only time").any { normalized.contains(it) } -> "time"
                listOf("только дата", "only date").any { normalized.contains(it) } -> "date"
                else -> "full"
            }
            steps += DirectAgentToolSpec(
                toolName = "get_current_datetime",
                args = JSONObject().apply {
                    if (timezone.isNotBlank()) put("timezone", timezone)
                    put("format", format)
                },
                planLine = if (russian) {
                    "Получить текущие дату и время."
                } else {
                    "Get the current date and time."
                }
            )
        }

        val systemInfoIntent = listOf("system info", "system", "система", "системе", "системную информацию", "информацию о системе").any {
            normalized.contains(it)
        }
        if (systemInfoIntent && "get_system_info" in enabledTools) {
            steps += DirectAgentToolSpec(
                toolName = "get_system_info",
                args = JSONObject(),
                planLine = if (russian) {
                    "Собрать краткую информацию о системе."
                } else {
                    "Read the current system information."
                }
            )
        }

        val wantsAirLlm = listOf("airllm", "air llm", "hugging face", "hf model", "большие модели", "модели больше", "больше миллиарда").any {
            normalized.contains(it)
        }
        val wantsAirLlmSetup = wantsAirLlm && listOf("setup", "install", "gateway", "script", "скрипт", "установ", "настрой", "интегр").any {
            normalized.contains(it)
        }
        if (wantsAirLlm && "airllm_status" in enabledTools) {
            steps += DirectAgentToolSpec(
                toolName = "airllm_status",
                args = JSONObject(),
                planLine = if (russian) {
                    "Проверить доступность AirLLM gateway."
                } else {
                    "Check AirLLM gateway readiness."
                }
            )
        }
        if (wantsAirLlmSetup && "airllm_write_gateway_script" in enabledTools) {
            steps += DirectAgentToolSpec(
                toolName = "airllm_write_gateway_script",
                args = JSONObject(),
                planLine = if (russian) {
                    "Создать Python gateway script для AirLLM."
                } else {
                    "Write the Python gateway script for AirLLM."
                }
            )
        }
        if (wantsAirLlm && !wantsAirLlmSetup && "airllm_generate" in enabledTools &&
            listOf("generate", "ask", "ответ", "спрос", "запрос").any { normalized.contains(it) }
        ) {
            steps += DirectAgentToolSpec(
                toolName = "airllm_generate",
                args = JSONObject().put("prompt", prompt).put("max_new_tokens", 256),
                planLine = if (russian) {
                    "Передать запрос в AirLLM gateway."
                } else {
                    "Send the request through AirLLM gateway."
                }
            )
        }

        val wantsOfficialGateway = listOf("official openclaw", "openclaw gateway", "гейтвей", "шлюз", "официальн").any {
            normalized.contains(it)
        }
        val wantsOfficialGatewaySetup = wantsOfficialGateway &&
            listOf("setup", "install", "script", "скрипт", "установ", "настрой", "интегр").any { normalized.contains(it) }
        val wantsOfficialGatewayTermux = wantsOfficialGateway &&
            listOf("termux", "термукс", "android", "андроид", "phone", "телефон", "эмулятор", "emulator", "локаль").any {
                normalized.contains(it)
            }
        val wantsOfficialGatewayStart = wantsOfficialGateway &&
            listOf("start", "run", "launch", "запусти", "запуск", "подними", "включ").any { normalized.contains(it) }
        if (wantsOfficialGateway && "openclaw_gateway_status" in enabledTools) {
            steps += DirectAgentToolSpec(
                toolName = "openclaw_gateway_status",
                args = JSONObject(),
                planLine = if (russian) {
                    "Проверить доступность официального OpenClaw Gateway."
                } else {
                    "Check official OpenClaw Gateway readiness."
                }
            )
        }
        if (wantsOfficialGatewayTermux && wantsOfficialGatewaySetup && "openclaw_gateway_termux_bootstrap" in enabledTools) {
            steps += DirectAgentToolSpec(
                toolName = "openclaw_gateway_termux_bootstrap",
                args = JSONObject(),
                planLine = if (russian) {
                    "Установить официальный OpenClaw CLI в Termux."
                } else {
                    "Install the official OpenClaw CLI in Termux."
                }
            )
        }
        if (wantsOfficialGatewayTermux && wantsOfficialGatewayStart && "openclaw_gateway_termux_start" in enabledTools) {
            steps += DirectAgentToolSpec(
                toolName = "openclaw_gateway_termux_start",
                args = JSONObject(),
                planLine = if (russian) {
                    "Запустить официальный OpenClaw Gateway локально через Termux."
                } else {
                    "Start the official OpenClaw Gateway locally through Termux."
                }
            )
        }
        if (wantsOfficialGatewayTermux && "openclaw_gateway_termux_status" in enabledTools) {
            steps += DirectAgentToolSpec(
                toolName = "openclaw_gateway_termux_status",
                args = JSONObject(),
                planLine = if (russian) {
                    "Проверить локальный OpenClaw Gateway через Termux CLI."
                } else {
                    "Probe the local OpenClaw Gateway through the Termux CLI."
                }
            )
        }
        if (wantsOfficialGatewaySetup && "openclaw_gateway_write_setup_scripts" in enabledTools) {
            steps += DirectAgentToolSpec(
                toolName = "openclaw_gateway_write_setup_scripts",
                args = JSONObject(),
                planLine = if (russian) {
                    "Создать scripts для запуска официального OpenClaw Gateway."
                } else {
                    "Write official OpenClaw Gateway setup scripts."
                }
            )
        }
        if (wantsOfficialGateway && !wantsOfficialGatewaySetup && "openclaw_gateway_send" in enabledTools &&
            listOf("send", "ask", "ответ", "спрос", "запрос").any { normalized.contains(it) }
        ) {
            steps += DirectAgentToolSpec(
                toolName = "openclaw_gateway_send",
                args = JSONObject().put("prompt", prompt).put("max_tokens", 1024),
                planLine = if (russian) {
                    "Передать запрос в официальный OpenClaw Gateway."
                } else {
                    "Send the request through the official OpenClaw Gateway."
                }
            )
        }

        val wantsGps = listOf("gps", "location", "геолокац", "локац", "джипиес").any { normalized.contains(it) }
        val wantsScript = listOf("script", "скрипт").any { normalized.contains(it) }
        val wantsStatusOnly = listOf("статус", "status", "проверь", "check").any { normalized.contains(it) } &&
            !wantsScript && !listOf("enable", "turn on", "включ", "disable", "turn off", "выключ", "on", "off").any {
                normalized.contains(it)
            }
        val locationStatusTool = firstEnabled("hermes_location_status", "get_location_status")
        if (wantsGps && wantsStatusOnly && locationStatusTool != null) {
            steps += DirectAgentToolSpec(
                toolName = locationStatusTool,
                args = JSONObject(),
                planLine = if (russian) {
                    "Проверить текущий статус геолокации."
                } else {
                    "Read the current location-services status."
                }
            )
        }
        if (wantsGps && wantsScript) {
            val enableLocation = listOf("enable", "turn on", "включ", "on").any { normalized.contains(it) } &&
                !listOf("disable", "turn off", "выключ", "off").any { normalized.contains(it) }
            val scriptPath = if (enableLocation) "automation/toggle_location_on.sh" else "automation/toggle_location_off.sh"
            val writeTool = firstEnabled("hermes_write_file", "create_file")
            if (writeTool != null) {
                val scriptBody = buildString {
                    appendLine("#!/system/bin/sh")
                    appendLine("cmd location set-location-enabled ${if (enableLocation) "true" else "false"}")
                }
                steps += DirectAgentToolSpec(
                    toolName = writeTool,
                    args = JSONObject()
                        .put("path", scriptPath)
                        .put("content", scriptBody)
                        .put("append", false),
                    planLine = if (russian) {
                        "Сохранить скрипт для переключения геолокации."
                    } else {
                        "Write a shell script for the requested location toggle."
                    }
                )
            }
            val runScriptTool = firstEnabled("hermes_run_script", "execute_script")
            if (runScriptTool != null) {
                steps += DirectAgentToolSpec(
                    toolName = runScriptTool,
                    args = JSONObject()
                        .put("path", scriptPath)
                        .put("interpreter", "sh")
                        .put("timeout_seconds", 12),
                    planLine = if (russian) {
                        "Запустить скрипт внутри приложения."
                    } else {
                        "Run the generated shell script inside the app sandbox."
                    }
                )
            }
            if (locationStatusTool != null) {
                steps += DirectAgentToolSpec(
                    toolName = locationStatusTool,
                    args = JSONObject(),
                    planLine = if (russian) {
                        "Проверить итоговый статус геолокации."
                    } else {
                        "Read back the current Android location-services state."
                    }
                )
            }
        }

        val wantsFileEdit = listOf("file", "files", "файл", "файлы", "write", "save", "создай", "запиши", "сохрани").any {
            normalized.contains(it)
        }
        val wantsDownloads = listOf("downloads", "download", "загрузк").any { normalized.contains(it) }
        val wantsBuyDipStrategy = listOf("buy dip", "buy the dip", "buy dpi", "бай дип", "стратег").any {
            normalized.contains(it)
        }
        val fileWriteTool = when {
            wantsDownloads && "create_file" in enabledTools -> "create_file"
            "hermes_write_file" in enabledTools -> "hermes_write_file"
            "create_file" in enabledTools -> "create_file"
            else -> null
        }
        if (wantsFileEdit && wantsBuyDipStrategy && fileWriteTool != null) {
            val fileName = buildBuyDipStrategyFileName(prompt)
            val relativePath = if (wantsDownloads && fileWriteTool == "create_file") "Downloads/$fileName" else fileName
            steps += DirectAgentToolSpec(
                toolName = fileWriteTool,
                args = JSONObject()
                    .put("path", relativePath)
                    .put("content", buildBuyDipStrategyText(prompt))
                    .put("append", false),
                planLine = if (russian) {
                    "Сохранить стратегию в текстовый файл."
                } else {
                    "Write the requested buy-the-dip strategy into a text file."
                }
            )
        }

        return steps
    }

    private fun extractMathExpression(prompt: String): String? {
        val match = Regex("""([0-9().,+\-*/%^ ]{3,})""").find(prompt)?.groupValues?.getOrNull(1)
        return match
            ?.replace(',', '.')
            ?.trim()
            ?.takeIf { candidate -> candidate.any { it.isDigit() } && candidate.any { it in "+-*/%^" } }
    }

    private suspend fun executeDirectToolSequence(
        specs: List<DirectAgentToolSpec>
    ): List<ToolChainStepData> {
        val steps = mutableListOf<ToolChainStepData>()
        _toolChainSteps.value = emptyList()
        var consecutiveFailures = 0

        specs.forEachIndexed { index, spec ->
            _currentToolChainRound.value = index + 1
            AppStateManager.setExecutingPlugin("", spec.toolName)
            val toolCall = ToolCall(name = spec.toolName, arguments = spec.args)
            val result = PluginManager.executeToolForMultiTurn(toolCall)
            val isSuccess = !result.isError

            if (isSuccess) {
                consecutiveFailures = 0
                AppStateManager.setPluginExecutionComplete(
                    pluginName = result.pluginName,
                    toolName = spec.toolName,
                    success = true,
                    executionTimeMs = result.executionTimeMs
                )
            } else {
                consecutiveFailures++
                AppStateManager.setPluginExecutionComplete(
                    pluginName = result.pluginName,
                    toolName = spec.toolName,
                    success = false,
                    executionTimeMs = result.executionTimeMs,
                    errorMessage = result.resultJson
                )
            }

            val step = ToolChainStepData(
                round = index + 1,
                toolName = spec.toolName,
                pluginName = result.pluginName,
                args = spec.args.toString(),
                result = result.resultJson.take(2000),
                executionTimeMs = result.executionTimeMs,
                success = isSuccess
            )
            steps += step
            _toolChainSteps.value = steps.toList()

            if (result.rawData != null) {
                val resultData = PluginResultData(
                    pluginName = result.pluginName,
                    toolName = spec.toolName,
                    inputParams = spec.args.toString(),
                    resultData = result.resultJson,
                    success = isSuccess
                )
                val pluginMessage = Messages(
                    role = Role.Assistant,
                    content = MessageContent(
                        contentType = ContentType.PluginResult,
                        content = "Plugin '${result.pluginName}' executed tool '${spec.toolName}'",
                        pluginResultData = resultData
                    ),
                    modelId = currentModelId,
                    pluginMetrics = PluginExecutionMetrics(
                        pluginName = result.pluginName,
                        toolName = spec.toolName,
                        executionTimeMs = result.executionTimeMs,
                        success = isSuccess
                    )
                )
                val pendingUserMsg = currentUserMessage
                if (!userMessageAdded.get() && pendingUserMsg != null) {
                    _messages.add(pendingUserMsg)
                    userMessageAdded.set(true)
                }
                _messages.add(pluginMessage)
            }

            if (consecutiveFailures >= 2) return steps
        }

        return steps
    }

    private fun buildFallbackPlan(prompt: String): String {
        val normalized = prompt.lowercase(Locale.ROOT)
        val russian = isRussianPrompt(prompt)
        return when {
            listOf("trend", "trends", "тренд", "тренды").any { normalized.contains(it) } ->
                if (russian) "1. Найти свежие сигналы по трендам ИИ.\n2. Коротко свести главное."
                else "1. Search the web for AI trend signals.\n2. Summarize the top findings for the user."
            listOf("gps", "location", "геолокац", "локац", "джипиес").any { normalized.contains(it) } ->
                if (russian) "1. Подготовить скрипт для геолокации.\n2. Запустить его, если Android разрешит.\n3. Проверить текущий статус."
                else "1. Prepare a location-control script.\n2. Run it if Android allows the action.\n3. Read back the location status."
            else ->
                if (russian) "1. Использовать подходящие локальные инструменты.\n2. Коротко и понятно показать результат."
                else "1. Use available local tools if they are relevant.\n2. Summarize the result clearly for the user."
        }
    }

    private fun buildDeterministicAgentSummary(
        prompt: String,
        steps: List<ToolChainStepData>
    ): String {
        val russian = isRussianPrompt(prompt)
        fun shorten(text: String, max: Int): String {
            val clean = text.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
            return if (clean.length <= max) clean else clean.take(max).trimEnd() + "..."
        }
        if (steps.isEmpty()) {
            return if (russian) "Не удалось выполнить локальные инструменты для этого запроса."
            else "I could not execute any local tools for this request."
        }
        val normalized = prompt.lowercase(Locale.ROOT)
        if (steps.any { it.toolName == "web_search" }) {
            val searchStep = steps.lastOrNull { it.toolName == "web_search" }
            if (searchStep != null) {
                runCatching {
                    val json = JSONObject(searchStep.result)
                    val results = json.optJSONArray("results")
                    val bullets = buildList {
                        if (results != null) {
                            for (i in 0 until minOf(results.length(), if (russian) 2 else 3)) {
                                val item = results.optJSONObject(i) ?: continue
                                val title = item.optString("title").ifBlank {
                                    if (russian) "Источник без названия" else "Untitled source"
                                }
                                val snippet = shorten(item.optString("snippet"), if (russian) 96 else 140)
                                val url = item.optString("url").trim()
                                add(
                                    if (russian) {
                                        buildString {
                                            append("• ")
                                            append(shorten(title, 72))
                                            if (snippet.isNotBlank()) {
                                                append(" — ")
                                                append(snippet)
                                            }
                                        }
                                    } else {
                                        buildString {
                                            append("- ")
                                            append(title)
                                            if (snippet.isNotBlank()) {
                                                append(": ")
                                                append(snippet)
                                            }
                                            if (url.isNotBlank()) {
                                                append(" (")
                                                append(url)
                                                append(")")
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                    if (bullets.isNotEmpty()) {
                        return buildString {
                            if (russian) {
                                appendLine("Коротко по трендам ИИ:")
                            } else {
                                appendLine("AI trends report")
                                appendLine()
                                if (listOf("2026", "2025", "2027").any { normalized.contains(it) }) {
                                    appendLine("Latest search results for the requested timeframe:")
                                } else {
                                    appendLine("Latest search results:")
                                }
                            }
                            bullets.forEach { appendLine(it) }
                        }.trim()
                    }
                }
            }
        }
        if (steps.any { it.toolName == "browser_open_url" || it.toolName == "hermes_open_browser" }) {
            val browserStep = steps.lastOrNull { it.toolName == "browser_open_url" || it.toolName == "hermes_open_browser" }
            val parsed = runCatching { browserStep?.result?.let { JSONObject(it) } }.getOrNull()
            val url = parsed?.optString("url").orEmpty().ifBlank {
                steps.lastOrNull { it.toolName == "browser_open_url" || it.toolName == "hermes_open_browser" }
                    ?.args
                    ?.let { runCatching { JSONObject(it).optString("url") }.getOrNull() }
                    .orEmpty()
            }
            return if (russian) {
                if (url.isNotBlank()) "Сайт открыт: $url" else "Сайт открыт."
            } else {
                if (url.isNotBlank()) "Opened: $url" else "Page opened."
            }
        }
        val hasScriptOrLocationStep = steps.any {
            it.toolName == "execute_script" ||
                it.toolName == "hermes_run_script" ||
                it.toolName == "get_location_status" ||
                it.toolName == "hermes_location_status"
        }
        if (!hasScriptOrLocationStep && steps.any { it.toolName == "create_file" || it.toolName == "hermes_write_file" }) {
            val fileStep = steps.lastOrNull { it.toolName == "create_file" || it.toolName == "hermes_write_file" }
            val parsed = runCatching { fileStep?.result?.let { JSONObject(it) } }.getOrNull()
            val path = parsed?.optString("path").orEmpty()
            val message = parsed?.optString("content").orEmpty().ifBlank { parsed?.optString("message").orEmpty() }
            return buildString {
                appendLine(if (russian) "Файл сохранён." else "File saved.")
                if (path.isNotBlank()) {
                    appendLine(if (russian) "Путь: $path" else "Path: $path")
                }
                if (!russian && message.isNotBlank()) {
                    appendLine(shorten(message, 140))
                }
            }.trim()
        }
        if (steps.any { it.toolName == "calculate" }) {
            val calcStep = steps.lastOrNull { it.toolName == "calculate" }
            val parsed = runCatching { calcStep?.result?.let { JSONObject(it) } }.getOrNull()
            val formatted = parsed?.optString("formattedResult").orEmpty()
            val expression = parsed?.optString("expression").orEmpty()
            return when {
                russian && formatted.isNotBlank() -> "Ответ: $formatted"
                formatted.isNotBlank() -> "Result: $formatted"
                russian && expression.isNotBlank() -> "Посчитал: $expression"
                expression.isNotBlank() -> "Calculated: $expression"
                russian -> "Расчёт выполнен."
                else -> "Calculation complete."
            }
        }
        if (steps.any { it.toolName == "get_current_datetime" }) {
            val timeStep = steps.lastOrNull { it.toolName == "get_current_datetime" }
            val parsed = runCatching { timeStep?.result?.let { JSONObject(it) } }.getOrNull()
            val result = parsed?.optString("result").orEmpty()
            val timezone = parsed?.optString("timezone").orEmpty()
            return when {
                russian && timezone.isNotBlank() && result.isNotBlank() -> "Сейчас: $result ($timezone)"
                russian && result.isNotBlank() -> "Сейчас: $result"
                result.isNotBlank() && timezone.isNotBlank() -> "Current time: $result ($timezone)"
                result.isNotBlank() -> "Current time: $result"
                russian -> "Время получено."
                else -> "Time fetched."
            }
        }
        if (steps.any { it.toolName == "get_system_info" }) {
            val systemStep = steps.lastOrNull { it.toolName == "get_system_info" }
            val parsed = runCatching { systemStep?.result?.let { JSONObject(it) } }.getOrNull()
            if (parsed != null) {
                val battery = parsed.optInt("batteryPercent", -1)
                val charging = parsed.optBoolean("isCharging", false)
                val network = parsed.optString("networkType").ifBlank { if (russian) "неизвестно" else "unknown" }
                val device = parsed.optString("deviceName").ifBlank { if (russian) "устройство" else "device" }
                return if (russian) {
                    buildString {
                        append("Система: ")
                        append(device)
                        append(". Сеть: ")
                        append(network)
                        if (battery >= 0) {
                            append(". Батарея: ")
                            append(battery)
                            append("%")
                            if (charging) append(", зарядка")
                        }
                    }
                } else {
                    buildString {
                        append("System: ")
                        append(device)
                        append(". Network: ")
                        append(network)
                        if (battery >= 0) {
                            append(". Battery: ")
                            append(battery)
                            append("%")
                            if (charging) append(", charging")
                        }
                }
                }
            }
        }
        if (steps.any { it.toolName == "termux_status" }) {
            val termuxStep = steps.lastOrNull { it.toolName == "termux_status" }
            val parsed = runCatching { termuxStep?.result?.let { JSONObject(it) } }.getOrNull()
            val message = parsed?.optString("message").orEmpty()
            val success = parsed?.optBoolean("success", false) == true
            return when {
                russian && message.contains("not installed", ignoreCase = true) -> "Termux не установлен."
                russian && message.contains("permission", ignoreCase = true) -> "Termux найден, но доступ не выдан."
                russian && message.contains("service is not visible", ignoreCase = true) -> "Termux найден, но сервис недоступен."
                russian && message.isNotBlank() -> "Termux: ${shorten(message, 110)}"
                message.isNotBlank() -> "Termux: $message"
                russian && success -> "Termux готов."
                russian -> "Termux недоступен."
                success -> "Termux is ready."
                else -> "Termux is unavailable."
            }
        }
        if (steps.any { it.toolName == "airllm_status" || it.toolName == "airllm_write_gateway_script" || it.toolName == "airllm_generate" }) {
            val airStep = steps.lastOrNull {
                it.toolName == "airllm_generate" ||
                    it.toolName == "airllm_write_gateway_script" ||
                    it.toolName == "airllm_status"
            }
            val parsed = runCatching { airStep?.result?.let { JSONObject(it) } }.getOrNull()
            val message = parsed?.optString("message").orEmpty()
            val text = parsed?.optString("text").orEmpty()
            val endpoint = parsed?.optString("endpoint").orEmpty()
            val scriptPath = parsed?.optString("scriptPath").orEmpty()
            return when {
                airStep?.toolName == "airllm_generate" && text.isNotBlank() -> shorten(text, if (russian) 700 else 1200)
                airStep?.toolName == "airllm_write_gateway_script" && russian ->
                    "AirLLM gateway script создан. Путь: $scriptPath"
                airStep?.toolName == "airllm_write_gateway_script" ->
                    "AirLLM gateway script created. Path: $scriptPath"
                russian && message.isNotBlank() && endpoint.isNotBlank() ->
                    "AirLLM: ${shorten(message, 140)} Endpoint: $endpoint"
                message.isNotBlank() && endpoint.isNotBlank() ->
                    "AirLLM: ${shorten(message, 180)} Endpoint: $endpoint"
                russian -> "AirLLM gateway пока недоступен. Запустите Python/PyTorch gateway и проверьте endpoint."
                else -> "AirLLM gateway is not reachable yet. Start the Python/PyTorch gateway and check the endpoint."
            }
        }
        if (steps.any {
                it.toolName == "openclaw_gateway_status" ||
                    it.toolName == "openclaw_gateway_models" ||
                    it.toolName == "openclaw_gateway_send" ||
                    it.toolName == "openclaw_gateway_write_setup_scripts" ||
                    it.toolName == "openclaw_gateway_tool_invoke" ||
                    it.toolName == "openclaw_gateway_termux_bootstrap" ||
                    it.toolName == "openclaw_gateway_termux_start" ||
                    it.toolName == "openclaw_gateway_termux_status" ||
                    it.toolName == "openclaw_gateway_termux_call"
            }
        ) {
            val gatewayStep = steps.lastOrNull {
                it.toolName.startsWith("openclaw_gateway_")
            }
            val parsed = runCatching { gatewayStep?.result?.let { JSONObject(it) } }.getOrNull()
            val message = parsed?.optString("message").orEmpty()
            val text = parsed?.optString("text").orEmpty()
            val endpoint = parsed?.optString("endpoint").orEmpty()
            val shellPath = parsed?.optString("shellPath").orEmpty()
            val cmdPath = parsed?.optString("cmdPath").orEmpty()
            val termuxStdout = parsed?.optString("termuxStdout").orEmpty()
            val termuxStderr = parsed?.optString("termuxStderr").orEmpty()
            return when {
                gatewayStep?.toolName == "openclaw_gateway_send" && text.isNotBlank() ->
                    shorten(text, if (russian) 700 else 1200)
                gatewayStep?.toolName?.startsWith("openclaw_gateway_termux_") == true && russian ->
                    buildString {
                        append("OpenClaw Gateway через Termux: ${shorten(message.ifBlank { "команда выполнена" }, 150)}")
                        if (termuxStdout.isNotBlank()) append("\n${shorten(termuxStdout, 600)}")
                        if (termuxStderr.isNotBlank()) append("\nОшибка: ${shorten(termuxStderr, 300)}")
                    }
                gatewayStep?.toolName?.startsWith("openclaw_gateway_termux_") == true ->
                    buildString {
                        append("OpenClaw Gateway through Termux: ${shorten(message.ifBlank { "command completed" }, 180)}")
                        if (termuxStdout.isNotBlank()) append("\n${shorten(termuxStdout, 700)}")
                        if (termuxStderr.isNotBlank()) append("\nError: ${shorten(termuxStderr, 320)}")
                    }
                gatewayStep?.toolName == "openclaw_gateway_write_setup_scripts" && russian ->
                    "Скрипты official OpenClaw Gateway созданы. Windows: $cmdPath Linux/Termux: $shellPath"
                gatewayStep?.toolName == "openclaw_gateway_write_setup_scripts" ->
                    "Official OpenClaw Gateway scripts created. Windows: $cmdPath Linux/Termux: $shellPath"
                russian && message.isNotBlank() && endpoint.isNotBlank() ->
                    "OpenClaw Gateway: ${shorten(message, 160)} Endpoint: $endpoint"
                message.isNotBlank() && endpoint.isNotBlank() ->
                    "OpenClaw Gateway: ${shorten(message, 180)} Endpoint: $endpoint"
                russian -> "Official OpenClaw Gateway пока недоступен. Запустите `openclaw gateway --port 18789` и проверьте endpoint."
                else -> "Official OpenClaw Gateway is not reachable yet. Start `openclaw gateway --port 18789` and check the endpoint."
            }
        }
        if (steps.any { it.toolName == "hermes_status" }) {
            val hermesStep = steps.lastOrNull { it.toolName == "hermes_status" }
            val parsed = runCatching { hermesStep?.result?.let { JSONObject(it) } }.getOrNull()
            val message = parsed?.optString("message").orEmpty()
            return when {
                russian && message.isNotBlank() -> "Hermes готов: ${shorten(message, 160)}"
                message.isNotBlank() -> "Hermes ready: ${shorten(message, 180)}"
                russian -> "Hermes готов к локальному агентному режиму."
                else -> "Hermes is ready for local agent mode."
            }
        }
        if (steps.any { it.toolName == "execute_script" || it.toolName == "hermes_run_script" || it.toolName == "get_location_status" || it.toolName == "hermes_location_status" }) {
            val scriptStep = steps.lastOrNull { it.toolName == "execute_script" || it.toolName == "hermes_run_script" }
            val locationStep = steps.lastOrNull { it.toolName == "get_location_status" || it.toolName == "hermes_location_status" }
            val statusLine = runCatching {
                locationStep?.result?.let { JSONObject(it) }?.let { json ->
                    val enabled = if (json.has("enabled")) json.optBoolean("enabled", false) else json.optBoolean("locationEnabled", false)
                    when (enabled) {
                        true -> if (russian) "Статус геолокации: включена." else "Current location status: enabled."
                        false -> if (russian) "Статус геолокации: выключена." else "Current location status: disabled."
                    }
                }
            }.getOrNull() ?: if (russian) "Статус геолокации не удалось подтвердить." else "Current location status could not be confirmed."
            val scriptLine = runCatching {
                scriptStep?.result?.let { JSONObject(it) }?.let { json ->
                    val message = json.optString("message").ifBlank {
                        if (russian) "Скрипт выполнен." else "Script finished."
                    }
                    val path = json.optString("scriptPath").ifBlank { json.optString("path") }
                    if (path.isNotBlank()) {
                        if (russian) "$message Скрипт: $path" else "$message Script: $path"
                    } else {
                        message
                    }
                }
            }.getOrNull() ?: if (russian) "Результат выполнения скрипта не получен." else "No script execution result was returned."
            return if (russian) {
                "Автоматизация: ${shorten(scriptLine, 110)} ${shorten(statusLine, 72)}".trim()
            } else {
                buildString {
                    appendLine("Location automation result")
                    appendLine()
                    appendLine(scriptLine)
                    appendLine(statusLine)
                }.trim()
            }
        }
        return buildString {
            appendLine(if (russian) "Результат" else "Request: $prompt")
            appendLine()
            if (!russian) {
                appendLine("Request: $prompt")
                appendLine()
            }
            appendLine(if (russian) "Выполненные шаги:" else "Executed steps:")
            steps.forEach { step ->
                append("- ")
                append(step.pluginName)
                append(" / ")
                append(step.toolName)
                append(": ")
                append(shorten(step.result, if (russian) 96 else 280))
                appendLine()
            }
        }.trim()
    }

    /** Persist agent chat results to vault. */
    private suspend fun persistAgentChat(
        prompt: String,
        isNewChat: Boolean,
        plan: String,
        steps: List<ToolChainStepData>,
        summary: String
    ) {
        val ragResultItems = _currentRagResults.value.takeIf { it.isNotEmpty() }?.map { result ->
            RagResultItem(
                ragName = result.ragName,
                content = result.content,
                score = result.score,
                nodeId = result.nodeId
            )
        }

        if (isNewChat) {
            chatManager.createNewChat().onSuccess { newChatId ->
                _currentChatId.value = newChatId
                chatManager.addUserMessage(newChatId, prompt)

                // Save plugin result messages
                _messages.filter { it.content.contentType == ContentType.PluginResult }
                    .forEach { chatManager.addMessage(newChatId, it) }

                // Save assistant message with full agent data
                chatManager.addAssistantMessage(
                    chatId = newChatId,
                    content = summary,
                    decodingMetrics = currentMetrics,
                    ragResults = ragResultItems,
                    toolChainSteps = steps,
                    agentPlan = plan,
                    agentSummary = summary
                )

                // Reload to get proper IDs
                chatManager.getChatMessages(newChatId).onSuccess { loadedMessages ->
                    _messages.clear()
                    _messages.addAll(loadedMessages)
                }

                AppStateManager.setGenerationComplete()
                AppStateManager.chatRefreshed()
                val spokenMsgId = _messages.lastOrNull { it.role == Role.Assistant }?.msgId
                resetStreamingState()
                viewModelScope.launch { autoSpeakIfEnabled(summary, spokenMsgId) }
            }.onFailure { e ->
                reportError("Failed to create chat: ${e.message}")
                resetStreamingState()
            }
        } else {
            val chatId = _currentChatId.value ?: return

            // Add user message to in-memory list if not already added
            val pendingUserMsg = currentUserMessage
            if (!userMessageAdded.get() && pendingUserMsg != null) {
                _messages.add(pendingUserMsg)
                userMessageAdded.set(true)
            }

            val assistantMessage = Messages(
                role = Role.Assistant,
                content = MessageContent(contentType = ContentType.Text, content = summary),
                modelId = currentModelId,
                decodingMetrics = currentMetrics,
                ragResults = ragResultItems,
                toolChainSteps = steps,
                agentPlan = plan,
                agentSummary = summary
            )
            _messages.add(assistantMessage)

            chatManager.addMessage(chatId, assistantMessage)

            AppStateManager.setGenerationComplete()
            AppStateManager.chatRefreshed()
            val spokenMsgId = assistantMessage.msgId
            resetStreamingState()
            viewModelScope.launch { autoSpeakIfEnabled(summary, spokenMsgId) }
        }
    }

    // ==================== Simple Flow (no tools) ====================

    private suspend fun simpleFlow(
        prompt: String,
        ragContext: String?,
        maxTokens: Int,
        isNewChat: Boolean,
        isRegeneration: Boolean = false
    ) {
        AppStateManager.setGeneratingText()
        val fullPrompt = ragContext?.let { "$it\n\n$prompt" } ?: prompt
        Log.d(TAG, "simpleFlow provider=$currentTextProviderType newChat=$isNewChat maxTokens=$maxTokens promptLen=${fullPrompt.length}")

        if (isNewChat) {
            val conversationMessages = buildConversationMessages(fullPrompt)
            val genResult = generateWithToolCalls(conversationMessages, maxTokens)
            val finalResponse = filterToolCallSyntax(genResult.text)
            Log.d(TAG, "simpleFlow newChat rawLen=${genResult.text.length} finalLen=${finalResponse.length}")

            _streamingAssistantMessage.value = finalResponse
            createChatWithMessages(prompt, finalResponse, currentMetrics)
        } else {
            val chatId = _currentChatId.value ?: return

            val conversationMessages = buildConversationMessages(fullPrompt, isRegeneration)
            val genResult = generateWithToolCalls(conversationMessages, maxTokens)
            val finalResponse = filterToolCallSyntax(genResult.text)
            Log.d(TAG, "simpleFlow existingChat rawLen=${genResult.text.length} finalLen=${finalResponse.length}")

            _streamingAssistantMessage.value = finalResponse

            val pendingUserMsg = currentUserMessage
            if (!userMessageAdded.get() && pendingUserMsg != null) {
                _messages.add(pendingUserMsg)
                userMessageAdded.set(true)
            }

            val ragResultItems = _currentRagResults.value.takeIf { it.isNotEmpty() }?.map { result ->
                RagResultItem(
                    ragName = result.ragName,
                    content = result.content,
                    score = result.score,
                    nodeId = result.nodeId
                )
            }

            if (finalResponse.isNotBlank()) {
                val assistantMessage = Messages(
                    role = Role.Assistant,
                    content = MessageContent(contentType = ContentType.Text, content = finalResponse),
                    modelId = currentModelId,
                    decodingMetrics = currentMetrics,
                    ragResults = ragResultItems
                )
                _messages.add(assistantMessage)
                chatManager.addMessage(chatId, assistantMessage)
                AppStateManager.setGenerationComplete()
                AppStateManager.chatRefreshed()
                val spokenMsgId = assistantMessage.msgId
                resetStreamingState()
                viewModelScope.launch { autoSpeakIfEnabled(finalResponse, spokenMsgId) }
            } else {
                AppStateManager.setGenerationComplete()
                resetStreamingState()
            }
        }
    }

    // ==================== LLM Generation Helpers ====================

    /**
     * Detect if text ends with a repeating pattern (common with small models).
     * Returns the index to trim to (keep one copy of the pattern), or -1 if no repetition.
     */
    private fun detectRepetitionTrimIndex(
        text: String,
        minPatternLen: Int = REPETITION_MIN_PATTERN_LEN,
        minRepeats: Int = REPETITION_MIN_REPEATS,
        maxCheckLen: Int = REPETITION_MAX_CHECK_LEN
    ): Int {
        if (text.length < minPatternLen * minRepeats) return -1

        val checkLen = minOf(text.length, maxCheckLen)
        val startOffset = text.length - checkLen
        val window = text.substring(startOffset)

        for (patternLen in minPatternLen until checkLen / minRepeats) {
            val pattern = window.substring(window.length - patternLen)
            var count = 1
            var pos = window.length - patternLen * 2

            while (pos >= 0) {
                if (window.regionMatches(pos, pattern, 0, patternLen)) {
                    count++
                    pos -= patternLen
                } else {
                    break
                }
            }

            if (count >= minRepeats && patternLen * count >= 120) {
                // Keep content up to end of first occurrence of the pattern
                val repeatStartInWindow = window.length - patternLen * count
                return startOffset + repeatStartInWindow + patternLen
            }
        }
        return -1
    }

    private data class GenerationResult(
        val text: String,
        val toolCalls: List<Pair<String, String>> = emptyList()
    )

    /** Generate text, streaming to UI. Collects any native ToolCall events. */
    private suspend fun generatePlainText(
        messages: List<JSONObject>,
        maxTokens: Int
    ): String {
        val result = generateWithToolCalls(messages, maxTokens)
        return result.text
    }

    private fun messagesToAirLlmPrompt(messages: List<JSONObject>): String {
        return buildString {
            messages.forEach { json ->
                val role = json.optString("role", "user").uppercase(Locale.ROOT)
                val content = json.optString("content", "").trim()
                if (content.isNotBlank()) {
                    append(role)
                    append(": ")
                    appendLine(content)
                }
            }
            append("ASSISTANT: ")
        }
    }

    private suspend fun generateWithToolCalls(
        messages: List<JSONObject>,
        maxTokens: Int
    ): GenerationResult {
        Log.d(TAG, "generateWithToolCalls provider=$currentTextProviderType messages=${messages.size} maxTokens=$maxTokens")
        val resultBuilder = StringBuilder()
        val utf8Buffer = Utf8TokenBuffer()
        val nativeToolCalls = mutableListOf<Pair<String, String>>()
        currentMetrics = null
        var lastEmitTime = 0L
        var lastRepCheckLen = 0
        var repetitionTrimIndex = -1

        val officialGatewaySettings = officialGatewaySettingsOrNull()
        val generationFlow = if (officialGatewaySettings != null) {
            flow {
                val response = officialOpenClawGatewayClient.chatCompletion(
                    rawEndpoint = officialGatewaySettings.officialGatewayEndpoint,
                    token = officialGatewaySettings.officialGatewayToken,
                    modelId = officialGatewaySettings.officialGatewayModelId.ifBlank {
                        OfficialOpenClawGatewayClient.DEFAULT_MODEL_ID
                    },
                    messages = messages,
                    maxTokens = maxTokens,
                )
                emit(GenerationEvent.Token(response.text))
                emit(GenerationEvent.Done)
            }
        } else when (currentTextProviderType) {
            ProviderType.GOOGLE_LOCAL -> {
                val googleMessages = messages.map { json ->
                    GoogleLocalMessage(
                        role = json.optString("role", "user"),
                        content = json.optString("content", "")
                    )
                }
                LlmModelWorker.googleLocalGenerateStreaming(appContext, googleMessages)
            }
            ProviderType.GGUF -> {
                val jsonArray = JSONArray(messages)
                LlmModelWorker.ggufGenerateMultiTurnStreaming(jsonArray.toString(), maxTokens)
            }
            ProviderType.AIRLLM_REMOTE -> flow {
                val settings = openClawSettingsStore.read()
                val prompt = messagesToAirLlmPrompt(messages)
                val response = airLlmGatewayClient.generate(
                    rawEndpoint = settings.airLlmEndpoint,
                    modelId = settings.airLlmModelId.ifBlank { activeAirLlmModelId.value.orEmpty() },
                    prompt = prompt,
                    maxNewTokens = maxTokens,
                )
                emit(GenerationEvent.Token(response.text))
                emit(GenerationEvent.Done)
            }
            else -> throw IllegalStateException("No text generation model is active")
        }

        generationFlow.collect { event ->
            when (event) {
                is GenerationEvent.Token -> {
                    val validText = utf8Buffer.append(event.text)
                    if (validText.isNotEmpty()) {
                        resultBuilder.append(validText)
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastEmitTime >= STREAMING_THROTTLE_MS) {
                        _streamingAssistantMessage.value = resultBuilder.toString()
                        lastEmitTime = now
                    }

                    // Periodically check for repetition loops
                    if (repetitionTrimIndex < 0 && resultBuilder.length - lastRepCheckLen >= REPETITION_CHECK_INTERVAL) {
                        lastRepCheckLen = resultBuilder.length
                        val trimIdx = detectRepetitionTrimIndex(resultBuilder.toString())
                        if (trimIdx >= 0) {
                            Log.w(TAG, "Repetition loop detected at ~$trimIdx chars, stopping generation")
                            repetitionTrimIndex = trimIdx
                            if (currentTextProviderType == ProviderType.GGUF) {
                                LlmModelWorker.ggufStopGeneration()
                            }
                        }
                    }
                }
                is GenerationEvent.Done -> {
                    // Flush any remaining buffered bytes
                    val remaining = utf8Buffer.flush()
                    if (remaining.isNotEmpty()) resultBuilder.append(remaining)
                    _streamingAssistantMessage.value = resultBuilder.toString()
                    // Update context usage after generation completes
                    _contextUsagePercent.value = LlmModelWorker.getContextUsageGguf()
                    Log.d(TAG, "generateWithToolCalls done rawLen=${resultBuilder.length}")
                }
                is GenerationEvent.Metrics -> { currentMetrics = event.metrics }
                is GenerationEvent.Progress -> { /* progress tracked elsewhere */ }
                is GenerationEvent.Error -> {
                    Log.e(TAG, "Generation error: ${event.message}")
                    throw Exception(event.message)
                }
                is GenerationEvent.ToolCall -> {
                    nativeToolCalls.add(Pair(event.name, event.args))
                    Log.d(TAG, "Native tool call received: ${event.name}")
                }
            }
        }

        var result = resultBuilder.toString().trim()
        if (result.isBlank()) {
            Log.w(TAG, "generateWithToolCalls completed with blank result for provider=$currentTextProviderType")
        }

        // Trim repetitive tail if detected during streaming
        if (repetitionTrimIndex in 1 until result.length) {
            Log.d(TAG, "Trimming repetitive output: keeping ${repetitionTrimIndex} of ${result.length} chars")
            result = result.substring(0, repetitionTrimIndex).trim()
            _streamingAssistantMessage.value = result
        }

        // Fallback: if no native ToolCall events, try text parsing
        if (nativeToolCalls.isEmpty() && result.isNotBlank()) {
            val enabledNames = PluginManager.getEnabledToolNames().map { it.lowercase() }
            parseToolCallsFromText(result)?.let { parsed ->
                val valid = parsed.filter { (name, _) ->
                    normalizeToolName(name).lowercase() in enabledNames
                }
                if (valid.size < parsed.size) {
                    Log.w(TAG, "Filtered out ${parsed.size - valid.size} hallucinated tool calls from fallback parsing")
                }
                nativeToolCalls.addAll(valid)
                if (valid.isNotEmpty()) {
                    Log.d(TAG, "Fallback parsed ${valid.size} tool calls from generateWithToolCalls text")
                }
            }
        }

        return GenerationResult(text = result, toolCalls = nativeToolCalls)
    }

    /** Generate with grammar and collect all tool calls from a single generation. */
    private suspend fun generateAndCollectToolCalls(
        messages: List<JSONObject>,
        maxTokens: Int
    ): List<Pair<String, String>> {
        if (currentTextProviderType == ProviderType.GOOGLE_LOCAL) {
            return generateWithToolCalls(messages, maxTokens).toolCalls
        }
        val toolCalls = mutableListOf<Pair<String, String>>()
        val textBuilder = StringBuilder()
        val jsonArray = JSONArray(messages)

        LlmModelWorker.ggufGenerateMultiTurnStreaming(
            jsonArray.toString(), maxTokens
        ).collect { event ->
            when (event) {
                is GenerationEvent.Token -> {
                    textBuilder.append(event.text)
                }
                is GenerationEvent.ToolCall -> {
                    toolCalls.add(Pair(event.name, event.args))
                    Log.d(TAG, "Collected tool call: ${event.name}")
                }
                is GenerationEvent.Done -> {}
                is GenerationEvent.Metrics -> { currentMetrics = event.metrics }
                is GenerationEvent.Progress -> { /* progress tracked elsewhere */ }
                is GenerationEvent.Error -> {
                    Log.e(TAG, "Generation error during tool call collection: ${event.message}")
                    throw Exception(event.message)
                }
            }
        }

        // Fallback: parse text if no ToolCall events were received
        val text = textBuilder.toString()
        if (toolCalls.isEmpty() && text.isNotBlank()) {
            Log.d(TAG, "No ToolCall events, trying text parsing fallback")
            val enabledNames = PluginManager.getEnabledToolNames().map { it.lowercase() }
            parseToolCallsFromText(text)?.let { parsed ->
                // Filter against enabled tools to reject hallucinated names
                val valid = parsed.filter { (name, _) ->
                    normalizeToolName(name).lowercase() in enabledNames
                }
                if (valid.size < parsed.size) {
                    Log.w(TAG, "Filtered out ${parsed.size - valid.size} hallucinated tool calls from fallback parsing")
                }
                toolCalls.addAll(valid)
                Log.d(TAG, "Fallback parsed ${valid.size} valid tool calls from text")
            }
        }

        return toolCalls
    }

    /** Parse multiple tool calls from text output (handles various formats). */
    private fun parseToolCallsFromText(text: String): List<Pair<String, String>>? {
        val results = mutableListOf<Pair<String, String>>()

        // Try: single tool call via existing parser
        tryParseToolCallFromContent(text)?.let { (name, args) ->
            results.add(Pair(name, args))
        }

        // Try: JSON array with tool_calls containing multiple entries
        if (results.isEmpty()) {
            try {
                val json = JSONObject(text.trim())
                val toolCallsArray = json.optJSONArray("tool_calls")
                if (toolCallsArray != null) {
                    for (i in 0 until toolCallsArray.length()) {
                        val call = toolCallsArray.getJSONObject(i)
                        val name = call.getString("name")
                        val args = call.getJSONObject("arguments").toString()
                        results.add(Pair(name, JSONObject().apply {
                            put("tool_calls", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("name", name)
                                    put("arguments", JSONObject(args))
                                })
                            })
                        }.toString()))
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Multi-tool JSON parse failed: ${e.message}")
            }
        }

        if (results.isEmpty()) {
            tryParseInstructionalToolCall(text)?.let { results.add(it) }
        }

        return results.takeIf { it.isNotEmpty() }
    }

    private fun tryParseInstructionalToolCall(content: String): Pair<String, String>? {
        val cleaned = content
            .replace("<|channel>thought", "", ignoreCase = true)
            .replace("<channel|>", "", ignoreCase = true)
            .replace("<turn|>", "", ignoreCase = true)
            .trim()

        val browserOpenRegex = Regex(
            """call\s+the\s+[`"]?(browser|browse|browser_open_url|hermes_open_browser)[`"]?\s+tool\s+with\s+the\s+argument\s+[`"]?([^`"\n]+?)[`"]?(?:[.!]|$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        browserOpenRegex.find(cleaned)?.let { match ->
            val rawUrl = match.groupValues[2].trim()
            val enabledNames = PluginManager.getEnabledToolNames().map { it.lowercase(Locale.ROOT) }.toSet()
            val toolName = if ("hermes_open_browser" in enabledNames) "hermes_open_browser" else "browser_open_url"
            val argsJson = JSONObject().apply {
                put("tool_calls", JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", toolName)
                        put("arguments", JSONObject().put("url", rawUrl))
                    })
                })
            }.toString()
            return Pair(toolName, argsJson)
        }

        val hermesStatusRegex = Regex(
            """call\s+the\s+[`"]?(hermes|hermes_status|openclaw_status)[`"]?\s+tool(?:[.!]|$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        if (hermesStatusRegex.containsMatchIn(cleaned)) {
            val toolName = "hermes_status"
            val argsJson = JSONObject().apply {
                put("tool_calls", JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", toolName)
                        put("arguments", JSONObject())
                    })
                })
            }.toString()
            return Pair(toolName, argsJson)
        }

        val webSearchRegex = Regex(
            """call\s+the\s+[`"]?(web_search|search)[`"]?\s+tool\s+with\s+the\s+argument\s+[`"]?([^`"\n]+?)[`"]?(?:[.!]|$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        webSearchRegex.find(cleaned)?.let { match ->
            val query = match.groupValues[2].trim()
            val toolName = "web_search"
            val argsJson = JSONObject().apply {
                put("tool_calls", JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", toolName)
                        put("arguments", JSONObject().put("query", query))
                    })
                })
            }.toString()
            return Pair(toolName, argsJson)
        }

        val locationStatusRegex = Regex(
            """call\s+the\s+[`"]?(get_location_status|location_status|hermes_location_status)[`"]?\s+tool(?:[.!]|$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        if (locationStatusRegex.containsMatchIn(cleaned)) {
            val enabledNames = PluginManager.getEnabledToolNames().map { it.lowercase(Locale.ROOT) }.toSet()
            val toolName = if ("hermes_location_status" in enabledNames) "hermes_location_status" else "get_location_status"
            val argsJson = JSONObject().apply {
                put("tool_calls", JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", toolName)
                        put("arguments", JSONObject())
                    })
                })
            }.toString()
            return Pair(toolName, argsJson)
        }

        val locationToggleRegex = Regex(
            """call\s+the\s+[`"]?(set_location_enabled|toggle_location)[`"]?\s+tool.*?\b(true|false|on|off|enable|disable)\b""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        locationToggleRegex.find(cleaned)?.let { match ->
            val desired = match.groupValues[2].lowercase() in setOf("true", "on", "enable")
            val toolName = "set_location_enabled"
            val argsJson = JSONObject().apply {
                put("tool_calls", JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", toolName)
                        put("arguments", JSONObject().put("enabled", desired))
                    })
                })
            }.toString()
            return Pair(toolName, argsJson)
        }

        return null
    }

    /**
     * Read the system prompt from the currently loaded model's config.
     * Returns empty string if no system prompt is configured.
     */
    private suspend fun getCurrentModelSystemPrompt(userQuery: String = ""): String {
        val basePrompt = if (currentTextProviderType == ProviderType.GGUF) {
            getGgufModelSchema().inferenceParams.systemPrompt
        } else {
            ""
        }

        val hasActiveTools = PluginManager.hasEnabledTools()
            && PluginManager.isToolCallingModelLoaded.value
        val thinkingDirective = if (_openClawEnabled.value && _thinkingModeEnabled.value && !hasActiveTools) "/think" else "/no_think"
        val openClawProfile = if (_openClawEnabled.value) {
            buildOpenClawProfilePrompt()
        } else {
            ""
        }

        return buildString {
            append(thinkingDirective)
            append("\nReturn only the final user-facing answer.")
            append("\nDo not expose internal reasoning, hidden chain-of-thought, or channel tags.")
            if (basePrompt.isNotEmpty()) {
                append("\n")
                append(basePrompt)
            }
            if (openClawProfile.isNotEmpty()) {
                append("\n")
                append(openClawProfile)
            }
        }
    }

    private fun buildOpenClawProfilePrompt(): String {
        val settings = runCatching { openClawSettingsStore.read() }.getOrNull() ?: return ""
        val skills = settings.selectedSkillIds.joinToString(", ").ifBlank { "hermes, browser, files, memory, travel_offline, automation, location_control, airllm, official_openclaw_gateway" }
        val tools = settings.selectedApiToolIds.joinToString(", ").ifBlank { "hermes, web_search, browser, system_info, location_control, airllm, official_openclaw_gateway" }
        return buildString {
            append("OpenClaw Local mode is active.")
            append("\nPrefer persistent agent behavior, tool-aware reasoning, and resumable task context.")
            append("\nSelected backend: ")
            append(settings.defaultBackend.name)
            append("\nEnabled skills: ")
            append(skills)
            append("\nAllowed tool families: ")
            append(tools)
            append("\nHermes agent layer is available for safe in-app browser handoff, sandboxed file/code write-read-list, sandbox shell scripts, location status, and automation readiness checks.")
            append("\nUse Hermes tools for local actions when they are enabled; use public Downloads only through the File Manager create_file tool.")
            append("\nWhen the user asks in Russian, answer in clear short Russian unless a longer report is explicitly requested.")
            if (settings.defaultBackend == LocalBackendOption.TERMUX_LOCAL || "termux" in settings.selectedSkillIds || "termux" in settings.selectedApiToolIds) {
                append("\nTermux bridge is available for shell, python, node, git, and longer-running local execution tasks.")
            }
            if (settings.defaultBackend == LocalBackendOption.AIRLLM_REMOTE || "airllm" in settings.selectedSkillIds || "airllm" in settings.selectedApiToolIds) {
                append("\nAirLLM gateway is available as a local/private-LAN Python/PyTorch backend for large Hugging Face models. Use airllm_status before airllm_generate.")
            }
            if (settings.defaultBackend == LocalBackendOption.OFFICIAL_GATEWAY ||
                settings.defaultBackend == LocalBackendOption.TERMUX_LOCAL ||
                "official_openclaw_gateway" in settings.selectedSkillIds ||
                "official_openclaw_gateway" in settings.selectedApiToolIds
            ) {
                append("\nOfficial OpenClaw Gateway is available through a local/private endpoint. For phone-local mode use openclaw_gateway_termux_bootstrap, openclaw_gateway_termux_start, and openclaw_gateway_termux_status. For already running gateways use openclaw_gateway_status, openclaw_gateway_models, openclaw_gateway_send, and openclaw_gateway_termux_call when raw WebSocket RPC through the official CLI is needed.")
            }
        }
    }

    /**
     * Build conversation messages for both new and existing chats.
     * @param isRegeneration when true, excludes the last user message from history
     *        and re-appends userPrompt at the end.
     */
    private suspend fun buildConversationMessages(
        userPrompt: String,
        isRegeneration: Boolean = false
    ): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        val systemPrompt = getCurrentModelSystemPrompt(userQuery = userPrompt)
        if (systemPrompt.isNotEmpty()) {
            result.add(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        // Always include history for regeneration (model needs context), otherwise respect setting
        if (chatMemoryEnabled.value || isRegeneration) {
            val excludeMsgId = if (isRegeneration) {
                _messages.lastOrNull { it.role == Role.User }?.msgId
            } else null

            _messages.forEach { msg ->
                if (excludeMsgId != null && msg.msgId == excludeMsgId) return@forEach
                when (msg.role) {
                    Role.User -> result.add(
                        JSONObject().put("role", "user").put("content", msg.content.content)
                    )
                    Role.Assistant -> {
                        val sanitizedAssistantContent = stripReasoningArtifacts(msg.content.content)
                        when (msg.content.contentType) {
                            ContentType.Text -> if (sanitizedAssistantContent.isNotBlank()) {
                                result.add(
                                    JSONObject().put("role", "assistant").put("content", sanitizedAssistantContent)
                                )
                            }
                            ContentType.PluginResult -> {
                                msg.content.pluginResultData?.let { data ->
                                    result.add(JSONObject().put("role", "assistant").put("content",
                                        "Tool '${data.toolName}' result: ${data.resultData.take(1000)}"
                                    ))
                                }
                            }
                            else -> {
                                if (sanitizedAssistantContent.isNotBlank()) {
                                    result.add(JSONObject().put("role", "assistant").put("content", sanitizedAssistantContent))
                                }
                            }
                        }
                    }
                }
            }
        }
        result.add(JSONObject().put("role", "user").put("content", userPrompt))
        return sanitizeRoleAlternation(result)
    }

    /** Ensure no two consecutive messages share the same role (required by llama.cpp chat templates). */
    private fun sanitizeRoleAlternation(messages: List<JSONObject>): List<JSONObject> {
        if (messages.size <= 1) return messages
        val result = mutableListOf(messages.first())
        for (i in 1 until messages.size) {
            val current = messages[i]
            val previous = result.last()
            if (current.getString("role") == previous.getString("role")
                && current.getString("role") != "system") {
                // Merge: append current content to previous
                val merged = previous.getString("content") + "\n" + current.getString("content")
                result[result.lastIndex] = JSONObject()
                    .put("role", previous.getString("role"))
                    .put("content", merged)
            } else {
                result.add(current)
            }
        }
        return result
    }

    // ==================== Tool Call Parsing Utilities ====================

    /**
     * Try to extract tool name and arguments from potentially malformed JSON.
     * Returns Pair(toolName, arguments JSONObject) or null if extraction fails.
     */
    private fun extractToolCallFromArgs(toolCallName: String, toolCallArgs: String): Pair<String, JSONObject>? {
        // Strategy 1: Parse as valid JSON with tool_calls array
        try {
            val argsObject = JSONObject(toolCallArgs)
            val toolCallsArray = argsObject.optJSONArray("tool_calls")
            if (toolCallsArray != null && toolCallsArray.length() > 0) {
                val firstCall = toolCallsArray.getJSONObject(0)
                return Pair(firstCall.getString("name"), firstCall.getJSONObject("arguments"))
            }
            // Maybe it's a direct {"name":"...","arguments":{...}} object
            if (argsObject.has("name") && argsObject.has("arguments")) {
                return Pair(argsObject.getString("name"), argsObject.getJSONObject("arguments"))
            }
        } catch (e: Exception) {
            Log.d(TAG, "Strategy 1 (full JSON) failed: ${e.message}")
        }

        // Strategy 2: Regex extract the first {"name":"...","arguments":{...}} from the text
        try {
            val nameArgRegex = Regex(
                """\{\s*"name"\s*:\s*"([^"]+)"\s*,\s*"arguments"\s*:\s*(\{[^}]*\})""",
                RegexOption.DOT_MATCHES_ALL
            )
            val match = nameArgRegex.find(toolCallArgs)
            if (match != null) {
                val name = match.groupValues[1]
                val argsStr = match.groupValues[2]
                return Pair(name, JSONObject(argsStr))
            }
        } catch (e: Exception) {
            Log.d(TAG, "Strategy 2 (regex name+args) failed: ${e.message}")
        }

        // Strategy 3: Extract arguments with nested braces (handles deeper JSON)
        try {
            val nameIdx = toolCallArgs.indexOf("\"name\"")
            val argsIdx = toolCallArgs.indexOf("\"arguments\"")
            if (nameIdx >= 0 && argsIdx >= 0) {
                val nameValRegex = Regex(""""name"\s*:\s*"([^"]+)"""")
                val nameMatch = nameValRegex.find(toolCallArgs)
                val name = nameMatch?.groupValues?.get(1) ?: toolCallName

                val argsStart = toolCallArgs.indexOf('{', argsIdx)
                if (argsStart >= 0) {
                    var depth = 0
                    var argsEnd = argsStart
                    for (i in argsStart until toolCallArgs.length) {
                        when (toolCallArgs[i]) {
                            '{' -> depth++
                            '}' -> {
                                depth--
                                if (depth == 0) {
                                    argsEnd = i
                                    break
                                }
                            }
                        }
                    }
                    val argsStr = toolCallArgs.substring(argsStart, argsEnd + 1)
                    return Pair(name, JSONObject(argsStr))
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Strategy 3 (balanced braces) failed: ${e.message}")
        }

        Log.e(TAG, "All JSON extraction strategies failed for: ${toolCallArgs.take(200)}")
        return null
    }

    /**
     * Try to parse a tool call from generated token content.
     * Handles Qwen XML format, JSON tool_calls array, and direct JSON objects.
     */
    private fun tryParseToolCallFromContent(content: String): Pair<String, String>? {
        try {
            // Format 1: Qwen <tool_call> XML tags
            val toolCallXmlRegex = Regex(
                "<tool_call>\\s*(\\{.*?\\})\\s*</tool_call>",
                RegexOption.DOT_MATCHES_ALL
            )
            val xmlMatch = toolCallXmlRegex.find(content)
            if (xmlMatch != null) {
                val jsonStr = xmlMatch.groupValues[1]
                val json = JSONObject(jsonStr)
                val name = json.getString("name")
                val argsJson = JSONObject().apply {
                    put("tool_calls", JSONArray().apply {
                        put(JSONObject().apply {
                            put("name", name)
                            put("arguments", json.getJSONObject("arguments"))
                        })
                    })
                }.toString()
                return Pair(name, argsJson)
            }

            // Format 2: JSON with tool_calls array
            val toolCallsJsonRegex = Regex(
                "\\{\\s*\"tool_calls\"\\s*:\\s*\\[.*?\\]\\s*\\}",
                RegexOption.DOT_MATCHES_ALL
            )
            val jsonMatch = toolCallsJsonRegex.find(content)
            if (jsonMatch != null) {
                val jsonStr = jsonMatch.value
                val json = JSONObject(jsonStr)
                val toolCallsArray = json.getJSONArray("tool_calls")
                if (toolCallsArray.length() > 0) {
                    val firstCall = toolCallsArray.getJSONObject(0)
                    val name = firstCall.getString("name")
                    return Pair(name, jsonStr)
                }
            }

            // Format 3: Direct JSON object with name and arguments
            val directJsonRegex = Regex(
                "\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"arguments\"\\s*:\\s*(\\{.*?\\})\\s*\\}",
                RegexOption.DOT_MATCHES_ALL
            )
            val directMatch = directJsonRegex.find(content)
            if (directMatch != null) {
                val name = directMatch.groupValues[1]
                val argsJson = JSONObject().apply {
                    put("tool_calls", JSONArray().apply {
                        put(JSONObject().apply {
                            put("name", name)
                            put("arguments", JSONObject(directMatch.groupValues[2]))
                        })
                    })
                }.toString()
                return Pair(name, argsJson)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse tool call from content: ${e.message}")
        }
        return null
    }

    /** Normalize model-emitted tool names to the app's registered tool names. */
    private fun normalizeToolName(toolName: String): String {
        val normalized = toolName.lowercase(Locale.ROOT).replace(" ", "_").replace("-", "_")
        return when (normalized) {
            "open_browser", "browser_open", "open_url", "browse_url" -> "browser_open_url"
            "write_file", "save_file", "file_write" -> "create_file"
            "read_file", "file_read" -> "read_text_file"
            "run_script", "script_run", "shell_script" -> "execute_script"
            "location_status", "gps_status" -> "get_location_status"
            "open_location", "location_settings", "gps_settings" -> "open_location_settings"
            "toggle_location", "gps_toggle" -> "set_location_enabled"
            "hermes_open_url", "hermes_browser_open" -> "hermes_open_browser"
            "hermes_file_write", "hermes_save_file" -> "hermes_write_file"
            "hermes_file_read" -> "hermes_read_file"
            "hermes_script", "hermes_execute_script" -> "hermes_run_script"
            "hermes_location", "hermes_gps_status" -> "hermes_location_status"
            "airllm", "airllm_call", "airllm_chat", "airllm_completion" -> "airllm_generate"
            "airllm_ready", "airllm_health" -> "airllm_status"
            "airllm_setup", "airllm_script" -> "airllm_write_gateway_script"
            "official_openclaw", "openclaw_gateway", "official_gateway", "openclaw_chat" -> "openclaw_gateway_send"
            "openclaw_gateway_ready", "openclaw_gateway_health", "openclaw_status" -> "openclaw_gateway_status"
            "openclaw_models", "openclaw_gateway_list_models" -> "openclaw_gateway_models"
            "openclaw_gateway_setup", "openclaw_setup", "openclaw_gateway_script" -> "openclaw_gateway_write_setup_scripts"
            "openclaw_gateway_termux_install", "openclaw_termux_bootstrap", "openclaw_termux_install" -> "openclaw_gateway_termux_bootstrap"
            "openclaw_gateway_termux_run", "openclaw_termux_start", "openclaw_termux_run" -> "openclaw_gateway_termux_start"
            "openclaw_termux_status", "openclaw_gateway_termux_health" -> "openclaw_gateway_termux_status"
            "openclaw_termux_call", "openclaw_rpc" -> "openclaw_gateway_termux_call"
            "openclaw_tool", "openclaw_invoke" -> "openclaw_gateway_tool_invoke"
            else -> normalized
        }
    }

    /** Filter out tool call syntax and code blocks from generated text. */
    private fun filterToolCallSyntax(content: String): String {
        var filtered = stripReasoningArtifacts(content)
        filtered = filtered.replace(Regex("<tool_call>\\s*\\{.*?\\}\\s*</tool_call>", RegexOption.DOT_MATCHES_ALL), "")
        filtered = filtered.replace(Regex("```json\\s*\\{[^`]*```", RegexOption.DOT_MATCHES_ALL), "")
        filtered = filtered.replace(Regex("```\\s*\\{[^`]*```", RegexOption.DOT_MATCHES_ALL), "")
        filtered = filtered.replace(Regex("\\{\\s*\"tool_calls\"\\s*:[^}]*\\}\\s*", RegexOption.DOT_MATCHES_ALL), "")
        filtered = filtered.replace(Regex("\\{\\s*\"name\"\\s*:\\s*\"[^\"]+\"\\s*,\\s*\"arguments\"\\s*:\\s*\\{.*?\\}\\s*\\}", RegexOption.DOT_MATCHES_ALL), "")
        filtered = filtered.trim()
        filtered = filtered.replace(Regex("\\n{3,}"), "\n\n")
        return filtered
    }

    private fun stripReasoningArtifacts(content: String): String {
        var filtered = content
        filtered = filtered.replace(
            Regex("<think>(.*?)</think>|\\[THINK](.*?)\\[/THINK]|<reasoning>(.*?)</reasoning>", RegexOption.DOT_MATCHES_ALL),
            ""
        )
        filtered = filtered.replace(
            Regex("<\\|channel>thought\\s*(.*?)(?=<\\|channel>\\w+|<channel\\|>|<\\|end\\|>|$)", RegexOption.DOT_MATCHES_ALL),
            ""
        )
        filtered = filtered.replace(Regex("<\\|channel>\\w+"), "")
        filtered = filtered.replace("<channel|>", "", ignoreCase = true)
        filtered = filtered.replace("<turn|>", "", ignoreCase = true)
        filtered = filtered.replace("<|end|>", "", ignoreCase = true)
        return filtered
    }

    // ==================== Image Generation ====================

    fun sendImageRequest(
        prompt: String,
        negativePrompt: String? = null,
        steps: Int? = null,
        cfgScale: Float? = null,
        seed: Long = -1L,
        width: Int? = null,
        height: Int? = null,
        scheduler: String? = null
    ) {
        if (!LlmModelWorker.isDiffusionModelLoaded.value) {
            reportError("Please load an image generation model first")
            return
        }

        if (_isGenerating.value) return
        _isGenerating.value = true

        viewModelScope.launch {
            try {
                val modelId = LlmModelWorker.currentDiffusionModelId.value
                if (modelId == null) {
                    reportError("Model configuration not found")
                    resetStreamingState()
                    return@launch
                }

                val config = getModelConfig(modelId)
                val inferenceParams = if (config != null) {
                    DiffusionInferenceParams.fromJson(config.modelInferenceParams)
                } else {
                    DiffusionInferenceParams()
                }
                val diffusionConfig = if (config != null) {
                    DiffusionConfig.fromJson(config.modelLoadingParams)
                } else {
                    DiffusionConfig()
                }

                val finalNegativePrompt = negativePrompt ?: inferenceParams.negativePrompt
                val finalSteps = steps ?: inferenceParams.steps
                val finalCfgScale = cfgScale ?: inferenceParams.cfgScale
                val finalWidth = width ?: diffusionConfig.width
                val finalHeight = height ?: diffusionConfig.height
                val finalScheduler = scheduler ?: inferenceParams.scheduler

                _streamingUserMessage.value = prompt
                imageGenerationStartTime = System.currentTimeMillis()
                userMessageAdded.set(false)

                if (isNewConversation) {
                    currentUserMessage = Messages(
                        msgId = "",
                        role = Role.User,
                        content = MessageContent(contentType = ContentType.Text, content = "Generate image: $prompt"),
                        modelId = LlmModelWorker.currentDiffusionModelId.value,
                            )
                    AppStateManager.setHasMessages(true)
                    generateImageForNewChat(prompt, finalNegativePrompt, finalSteps, finalCfgScale, seed, finalWidth, finalHeight, finalScheduler, inferenceParams.showDiffusionProcess, inferenceParams.showDiffusionStride)
                } else {
                    val chatId = _currentChatId.value
                    if (chatId == null) {
                        reportError("No chat selected")
                        resetStreamingState()
                        return@launch
                    }
                    chatManager.addUserMessage(chatId, "Generate image: $prompt").onSuccess { userMessage ->
                        currentUserMessage = userMessage
                        AppStateManager.setHasMessages(true)
                        generateImage(chatId, userMessage, prompt, finalNegativePrompt, finalSteps, finalCfgScale, seed, finalWidth, finalHeight, finalScheduler, inferenceParams.showDiffusionProcess, inferenceParams.showDiffusionStride)
                    }.onFailure { e ->
                        reportError("Failed to save message: ${e.message}")
                        resetStreamingState()
                    }
                }
            } catch (e: Exception) {
                reportError(e.message)
                resetStreamingState()
            }
        }
    }

    suspend fun getModelConfig(modelId: String): com.santiya.localaihub.models.table_schema.ModelConfig? {
        return AppContainer.getModelRepository().getConfigByModelId(modelId)
    }


    private fun generateImageForNewChat(
        prompt: String, negativePrompt: String, steps: Int, cfgScale: Float,
        seed: Long, width: Int, height: Int, scheduler: String,
        showDiffusionProcess: Boolean = true, showDiffusionStride: Int = 1
    ) {
        generationJob = viewModelScope.launch {
            _error.value = null
            _streamingImage.value = null
            _imageGenerationProgress.value = 0f
            currentGeneratedImage = null
            _isGenerating.value = true
            AppStateManager.setGeneratingImage()

            try {
                LlmModelWorker.generateDiffusionImage(prompt, negativePrompt, steps, cfgScale, seed, width, height, scheduler, showDiffusionProcess = showDiffusionProcess, showDiffusionStride = showDiffusionStride).collect { event ->
                    when (event) {
                        is LlmModelWorker.DiffusionGenerationEvent.Progress -> {
                            _imageGenerationProgress.value = event.progress
                            _imageGenerationStep.value = "Step ${event.currentStep}/${event.totalSteps}"
                            event.intermediateImage?.let { _streamingImage.value = it }
                        }
                        is LlmModelWorker.DiffusionGenerationEvent.Complete -> {
                            _imageGenerationProgress.value = 1f
                            _streamingImage.value = event.image
                            currentGeneratedImage = event.image
                            val generationTime = System.currentTimeMillis() - imageGenerationStartTime
                            currentImageMetrics = ImageGenerationMetrics(steps = steps, cfgScale = cfgScale, seed = event.seed, width = event.width, height = event.height, scheduler = scheduler, generationTimeMs = generationTime)
                            _isGenerating.value = false
                            val imageBase64 = LlmModelWorker.bitmapToBase64(event.image)
                            createChatWithImageMessage("Generate image: $prompt", imageBase64, prompt, event.seed)
                        }
                        is LlmModelWorker.DiffusionGenerationEvent.Error -> {
                            handleImageGenerationError(prompt, event.message)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                handleImageGenerationException(prompt, e)
            }
        }
    }

    private fun generateImage(
        chatId: String, userMessage: Messages, prompt: String, negativePrompt: String,
        steps: Int, cfgScale: Float, seed: Long, width: Int, height: Int, scheduler: String,
        showDiffusionProcess: Boolean = true, showDiffusionStride: Int = 1
    ) {
        generationJob = viewModelScope.launch {
            _error.value = null
            _streamingImage.value = null
            _imageGenerationProgress.value = 0f
            _isGenerating.value = true
            AppStateManager.setGeneratingImage()

            try {
                LlmModelWorker.generateDiffusionImage(prompt, negativePrompt, steps, cfgScale, seed, width, height, scheduler, showDiffusionProcess = showDiffusionProcess, showDiffusionStride = showDiffusionStride).collect { event ->
                    when (event) {
                        is LlmModelWorker.DiffusionGenerationEvent.Progress -> {
                            _imageGenerationProgress.value = event.progress
                            _imageGenerationStep.value = "Step ${event.currentStep}/${event.totalSteps}"
                            event.intermediateImage?.let { _streamingImage.value = it }
                        }
                        is LlmModelWorker.DiffusionGenerationEvent.Complete -> {
                            _imageGenerationProgress.value = 1f
                            _streamingImage.value = event.image
                            _isGenerating.value = false
                            val generationTime = System.currentTimeMillis() - imageGenerationStartTime
                            currentImageMetrics = ImageGenerationMetrics(steps = steps, cfgScale = cfgScale, seed = event.seed, width = event.width, height = event.height, scheduler = scheduler, generationTimeMs = generationTime)
                            if (!userMessageAdded.get()) { _messages.add(userMessage); userMessageAdded.set(true) }
                            val imageBase64 = LlmModelWorker.bitmapToBase64(event.image)
                            val imageMessage = Messages(
                                role = Role.Assistant,
                                content = MessageContent(contentType = ContentType.Image, content = "Generated image for: $prompt", imageData = imageBase64, imagePrompt = prompt, imageSeed = event.seed),
                                modelId = LlmModelWorker.currentDiffusionModelId.value,
                                            imageMetrics = currentImageMetrics
                            )
                            _messages.add(imageMessage)
                            chatManager.addImageMessage(chatId, imageBase64, prompt, event.seed, currentImageMetrics)
                            AppStateManager.setGenerationComplete()
                            AppStateManager.chatRefreshed()
                            resetStreamingState()
                        }
                        is LlmModelWorker.DiffusionGenerationEvent.Error -> {
                            handleImageGenerationErrorExisting(chatId, userMessage, prompt, event.message)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                handleImageGenerationExceptionExisting(chatId, userMessage, prompt, e)
            }
        }
    }

    // ==================== Helper Functions ====================

    private suspend fun createChatWithMessages(
        userPrompt: String,
        assistantResponse: String,
        metrics: DecodingMetrics?,
        toolChainSteps: List<ToolChainStepData>? = null
    ) {
        val filteredResponse = filterToolCallSyntax(assistantResponse)
        val ragResultItems = _currentRagResults.value.takeIf { it.isNotEmpty() }?.map { result ->
            RagResultItem(ragName = result.ragName, content = result.content, score = result.score, nodeId = result.nodeId)
        }
        val pluginResults = _messages.filter { it.content.contentType == ContentType.PluginResult }

        chatManager.createNewChat().onSuccess { newChatId ->
            _currentChatId.value = newChatId
            val userMsg = Messages(
                role = Role.User,
                content = MessageContent(contentType = ContentType.Text, content = userPrompt),
                modelId = currentModelId,
                )
            chatManager.addMessage(newChatId, userMsg).onSuccess {
                pluginResults.forEach { pluginMsg ->
                    chatManager.addMessage(newChatId, pluginMsg)
                }
                if (filteredResponse.isNotBlank()) {
                    val assistantMsg = Messages(
                        role = Role.Assistant,
                        content = MessageContent(contentType = ContentType.Text, content = filteredResponse),
                        modelId = currentModelId,
                            decodingMetrics = metrics,
                        ragResults = ragResultItems,
                        toolChainSteps = toolChainSteps
                    )
                    chatManager.addMessage(newChatId, assistantMsg)
                }
                chatManager.getChatMessages(newChatId).onSuccess { loadedMessages ->
                    _messages.clear()
                    _messages.addAll(loadedMessages)
                    AppStateManager.setGenerationComplete()
                    AppStateManager.chatRefreshed()
                    val spokenMsgId = loadedMessages.lastOrNull { it.role == Role.Assistant }?.msgId
                    resetStreamingState()
                    viewModelScope.launch { autoSpeakIfEnabled(filteredResponse, spokenMsgId) }
                }.onFailure {
                    AppStateManager.setGenerationComplete()
                    resetStreamingState()
                }
            }.onFailure { e ->
                reportError("Failed to save chat: ${e.message}")
            }
        }.onFailure { e ->
            reportError("Failed to create chat: ${e.message}")
        }
    }

    private suspend fun createChatWithImageMessage(
        userPrompt: String, imageBase64: String, imagePrompt: String, seed: Long
    ) {
        val diffusionModelId = LlmModelWorker.currentDiffusionModelId.value
        chatManager.createNewChat().onSuccess { newChatId ->
            _currentChatId.value = newChatId
            val userMsg = Messages(
                role = Role.User,
                content = MessageContent(contentType = ContentType.Text, content = userPrompt),
                modelId = diffusionModelId,
                )
            chatManager.addMessage(newChatId, userMsg).onSuccess { userMessage ->
                _messages.add(userMessage)
                userMessageAdded.set(true)
                val imageMsg = Messages(
                    role = Role.Assistant,
                    content = MessageContent(
                        contentType = ContentType.Image,
                        content = "Generated image for: $imagePrompt",
                        imageData = imageBase64,
                        imagePrompt = imagePrompt,
                        imageSeed = seed
                    ),
                    modelId = diffusionModelId,
                    imageMetrics = currentImageMetrics
                )
                chatManager.addMessage(newChatId, imageMsg).onSuccess { imageMessage ->
                    _messages.add(imageMessage)
                    AppStateManager.setGenerationComplete()
                    AppStateManager.chatRefreshed()
                    resetStreamingState()
                }
            }.onFailure { e ->
                reportError("Failed to save chat: ${e.message}")
            }
        }.onFailure { e ->
            reportError("Failed to create chat: ${e.message}")
        }
    }

    // ==================== Error Handlers ====================

    private fun handleImageGenerationError(prompt: String, errorMessage: String) {
        _isGenerating.value = false
        reportError(errorMessage)
        resetStreamingState()
    }

    private fun handleImageGenerationException(prompt: String, exception: Exception) {
        _isGenerating.value = false
        reportError(exception.message)
        resetStreamingState()
    }

    private fun handleImageGenerationErrorExisting(chatId: String, userMessage: Messages, prompt: String, errorMessage: String) {
        _isGenerating.value = false
        reportError(errorMessage)
        if (!userMessageAdded.get()) { _messages.add(userMessage); userMessageAdded.set(true) }
        _messages.add(Messages(role = Role.Assistant, content = MessageContent(contentType = ContentType.Text, content = "Error generating image: $errorMessage")))
        resetStreamingState()
    }

    private fun handleImageGenerationExceptionExisting(chatId: String, userMessage: Messages, prompt: String, exception: Exception) {
        _isGenerating.value = false
        reportError(exception.message)
        if (!userMessageAdded.get()) { _messages.add(userMessage); userMessageAdded.set(true) }
        resetStreamingState()
    }

    private fun reportError(message: String?) {
        val msg = message ?: "Unknown error"
        _error.value = msg
        AppStateManager.setError(msg)
    }

    private fun resetStreamingState() {
        _isGenerating.value = false
        _streamingUserMessage.value = null
        _streamingAssistantMessage.value = ""
        _streamingImage.value = null
        _imageGenerationProgress.value = 0f
        _imageGenerationStep.value = ""
        currentUserMessage = null
        currentGeneratedImage = null
        currentMetrics = null
        currentImageMetrics = null
        userMessageAdded.set(false)
        _toolChainSteps.value = emptyList()
        _currentToolChainRound.value = 0
        _agentPhase.value = AgentPhase.Idle
        _agentPlan.value = null
        _agentSummary.value = null
        _currentRagContext.value = null
        _currentRagResults.value = emptyList()
    }

    // ==================== Generation Control ====================

    fun stop() {
        if (TTSManager.isPlaying.value) { TTSManager.stopPlayback() }

        // 1. Snapshot mutable state BEFORE cancellation nukes it via finallyРІвЂ вЂ™resetStreamingState
        val snapshotChatId = _currentChatId.value
        val snapshotUserMsg = currentUserMessage
        val snapshotContent = _streamingAssistantMessage.value
        val snapshotMetrics = currentMetrics
        val snapshotImage = currentGeneratedImage
        val snapshotImageMetrics = currentImageMetrics
        val snapshotUserAdded = userMessageAdded.get()

        // 2. Stop native generation (synchronous signal to engine)
        when (_currentGenerationType.value) {
            ModelType.TEXT_GENERATION -> {
                LlmModelWorker.ggufStopGeneration()
            }
            ModelType.IMAGE_GENERATION -> LlmModelWorker.stopDiffusionGeneration()
            ModelType.AUDIO_GENERATION -> stopTTS()
        }

        // 3. Cancel the coroutine job (triggers finally РІвЂ вЂ™ resetStreamingState)
        generationJob?.cancel()
        generationJob = null

        // 4. Persist partial results using snapshots taken before cancellation
        when (_currentGenerationType.value) {
            ModelType.TEXT_GENERATION -> handleTextStop(
                snapshotChatId, snapshotUserMsg, snapshotContent, snapshotMetrics, snapshotUserAdded
            )
            ModelType.IMAGE_GENERATION -> handleImageStop(
                snapshotChatId, snapshotUserMsg, snapshotImage, snapshotImageMetrics, snapshotUserAdded
            )
            else -> resetStreamingState()
        }

        AppStateManager.setGenerationComplete()
    }

    private fun handleTextStop(
        chatId: String?,
        userMsg: Messages?,
        content: String,
        metrics: DecodingMetrics?,
        wasUserAdded: Boolean
    ) {
        // Add messages SYNCHRONOUSLY before resetting streaming state,
        // so there's no frame where streaming UI is cleared but messages aren't in the list.
        if (chatId != null && userMsg != null && content.isNotEmpty()) {
            if (!wasUserAdded) { _messages.add(userMsg) }
            val assistantMessage = Messages(
                role = Role.Assistant,
                content = MessageContent(contentType = ContentType.Text, content = "$content [stopped]"),
                modelId = currentModelId,
                decodingMetrics = metrics
            )
            _messages.add(assistantMessage)
            // New content was produced РІР‚вЂќ safe to delete old message from DB
            regenerationSnapshot?.let { old ->
                regenerationSnapshot = null
                viewModelScope.launch { chatManager.deleteMessage(old.msgId) }
            }
            // Persist new message to DB async
            viewModelScope.launch { chatManager.addMessage(chatId, assistantMessage) }
        } else if (regenerationSnapshot != null) {
            // Regeneration cancelled with no content РІР‚вЂќ restore old message
            restoreRegenerationSnapshot()
        } else if (userMsg != null && !wasUserAdded) {
            _messages.add(userMsg)
        }

        // Restore grammar in case we stopped mid-agent-flow
        try { PluginManager.restoreGrammar() } catch (_: Exception) {}
        resetStreamingState()
    }

    private fun handleImageStop(
        chatId: String?,
        userMsg: Messages?,
        image: Bitmap?,
        imgMetrics: ImageGenerationMetrics?,
        wasUserAdded: Boolean
    ) {
        if (chatId != null && userMsg != null && image != null) {
            if (!wasUserAdded) { _messages.add(userMsg) }
            val imageBase64 = LlmModelWorker.bitmapToBase64(image)
            val imageMessage = Messages(
                role = Role.Assistant,
                content = MessageContent(contentType = ContentType.Image, content = "Image generation stopped", imageData = imageBase64),
                modelId = LlmModelWorker.currentDiffusionModelId.value,
                imageMetrics = imgMetrics
            )
            _messages.add(imageMessage)
            // Persist to DB async
            viewModelScope.launch { chatManager.addMessage(chatId, imageMessage) }
        } else if (userMsg != null && !wasUserAdded) {
            _messages.add(userMsg)
        }

        resetStreamingState()
    }

    // ==================== TTS Controls ====================

    private suspend fun autoSpeakIfEnabled(text: String, msgId: String? = null) {
        if (text.isBlank()) return
        val settings = ttsDataStore.settings.first()
        if (!settings.autoSpeak) return

        if (!TTSManager.isLoaded()) {
            val modelDir = TTSManager.getModelDirectory() ?: return
            withContext(Dispatchers.IO) {
                TTSManager.loadModel(modelDir, settings.useNNAPI)
            }
            if (!TTSManager.isLoaded()) return
        }

        TTSManager.speak(text = text, settings = settings, msgId = msgId)
    }

    fun speakMessage(message: Messages) {
        if (message.content.contentType != ContentType.Text) return
        val text = message.content.content
        if (text.isBlank()) return

        viewModelScope.launch {
            if (!TTSManager.isLoaded()) {
                val modelDir = TTSManager.getModelDirectory()
                if (modelDir == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "Download the TTS voice model from Model Store to enable speech", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val settings = ttsDataStore.settings.first()
                withContext(Dispatchers.IO) { TTSManager.loadModel(modelDir, settings.useNNAPI) }
                if (!TTSManager.isLoaded()) return@launch
            }
            val settings = ttsDataStore.settings.first()
            TTSManager.speak(text = text, settings = settings, msgId = message.msgId)
        }
    }

    fun stopTTS() {
        TTSManager.stopPlayback()
    }

    // ==================== UI Controls ====================

    fun clearMessages() {
        _messages.clear()
        resetStreamingState()
        _error.value = null
        AppStateManager.setHasMessages(false)
    }

    fun clearError() {
        _error.value = null
        AppStateManager.clearError()
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            chatManager.deleteMessage(messageId).onSuccess {
                _messages.removeIf { it.msgId == messageId }
            }.onFailure { e ->
                reportError("Failed to delete message: ${e.message}")
            }
        }
    }

    fun showDynamicWindow() {
        _showDynamicWindow.value = _showDynamicWindow.value.not()
    }

    fun hideDynamicWindow() {
        _showDynamicWindow.value = false
    }

    fun showModelList() {
        _showModelList.value = true
    }

    fun hideModelList() {
        _showModelList.value = false
    }

    // РІвЂќР‚РІвЂќР‚ Lifecycle РІвЂќР‚РІвЂќР‚

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
        generationJob = null
    }

    // РІвЂќР‚РІвЂќР‚ UTF-8 Token Buffer РІвЂќР‚РІвЂќР‚

    /**
     * Buffers incomplete UTF-8 byte sequences from streaming tokens.
     * Some models emit tokens that split multi-byte characters (e.g. Turkish Р•Сџ, emoji)
     * across multiple callbacks. This buffer holds trailing incomplete bytes until
     * the next token completes the character.
     */
    private class Utf8TokenBuffer {
        private val pending = ByteArray(4) // Max UTF-8 char is 4 bytes
        private var pendingLen = 0

        fun append(token: String): String {
            if (token.isEmpty()) return ""
            val bytes = token.toByteArray(Charsets.UTF_8)

            // Prepend any pending bytes from last call
            val combined = if (pendingLen > 0) {
                ByteArray(pendingLen + bytes.size).also {
                    pending.copyInto(it, 0, 0, pendingLen)
                    bytes.copyInto(it, pendingLen)
                }
            } else bytes

            // Find last complete UTF-8 character boundary
            val completeLen = findCompleteUtf8Length(combined)
            pendingLen = combined.size - completeLen
            if (pendingLen > 0) {
                combined.copyInto(pending, 0, completeLen, combined.size)
            }

            return if (completeLen > 0) String(combined, 0, completeLen, Charsets.UTF_8) else ""
        }

        fun flush(): String {
            if (pendingLen == 0) return ""
            // Force-decode whatever is left (replacement chars for truly invalid bytes)
            val result = String(pending, 0, pendingLen, Charsets.UTF_8)
            pendingLen = 0
            return result
        }

        private fun findCompleteUtf8Length(bytes: ByteArray): Int {
            if (bytes.isEmpty()) return 0
            // Walk backwards from end to find if the last char is incomplete
            var i = bytes.size - 1
            // Skip continuation bytes (10xxxxxx)
            while (i >= 0 && bytes[i].toInt() and 0xC0 == 0x80) i--
            if (i < 0) return 0 // All continuation bytes РІР‚вЂќ all incomplete

            val leadByte = bytes[i].toInt() and 0xFF
            val expectedLen = when {
                leadByte and 0x80 == 0 -> 1    // 0xxxxxxx
                leadByte and 0xE0 == 0xC0 -> 2 // 110xxxxx
                leadByte and 0xF0 == 0xE0 -> 3 // 1110xxxx
                leadByte and 0xF8 == 0xF0 -> 4 // 11110xxx
                else -> 1 // Invalid lead byte, treat as single
            }

            val actualLen = bytes.size - i
            return if (actualLen >= expectedLen) bytes.size else i
        }
    }

    companion object {
        private const val TAG = "ChatViewModel"
        private const val PLAN_MAX_TOKENS = 150
        private const val SUMMARY_MAX_TOKENS = 512
        private const val STREAMING_THROTTLE_MS = 100L
        private const val REPETITION_CHECK_INTERVAL = 200
        private const val REPETITION_MIN_PATTERN_LEN = 30
        private const val REPETITION_MIN_REPEATS = 4
        private const val REPETITION_MAX_CHECK_LEN = 800
    }
}
