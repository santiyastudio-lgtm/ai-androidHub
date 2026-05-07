package com.santiya.localaihub.engine

import android.content.Context
import android.util.Log
import com.arm.aichat.InferenceEngine
import com.dark.gguf_lib.GGMLEngine
import com.dark.gguf_lib.toolcalling.GrammarMode
import com.dark.gguf_lib.toolcalling.ToolCallingConfig
import com.dark.gguf_lib.toolcalling.ToolDefinitionBuilder
import com.santiya.localaihub.di.AppContainer
import com.santiya.localaihub.distributedruntime.SourceDistributedRuntime
import com.santiya.localaihub.global.DeviceTuner
import com.santiya.localaihub.global.HardwareScanner
import com.santiya.localaihub.models.engine_schema.DecodingMetrics
import com.santiya.localaihub.models.engine_schema.GgufEngineSchema
import com.santiya.localaihub.models.engine_schema.GgufLoadingParams
import com.santiya.localaihub.models.engine_schema.toLocal
import com.santiya.localaihub.models.table_schema.Model
import com.santiya.localaihub.models.table_schema.ModelConfig
import com.santiya.localaihub.worker.GgufRuntimeBackend
import com.santiya.localaihub.worker.GgufRuntimeSupport
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import com.dark.gguf_lib.models.GenerationEvent as LibGenerationEvent

class GGUFEngine {
    private enum class ActiveBackend {
        LEGACY,
        SOURCE,
    }

    private val engine = GGMLEngine()
    private var sourceRuntime: SourceDistributedRuntime? = null
    private var activeBackend = ActiveBackend.LEGACY
    private var currentModelId: String? = null
    private var lastLoadErrorMessage: String? = null

    private var currentToolsJson: String? = null
    private var currentToolCallingConfig: ToolCallingConfig? = null

    val isLoaded: Boolean
        get() = when (activeBackend) {
            ActiveBackend.LEGACY -> engine.isLoaded
            ActiveBackend.SOURCE -> sourceRuntime?.state?.value is InferenceEngine.State.ModelReady
        }

    fun getLastLoadErrorMessage(): String? = lastLoadErrorMessage

    suspend fun load(model: Model, config: ModelConfig?): Boolean = withContext(Dispatchers.IO) {
        unload()
        lastLoadErrorMessage = null

        val schema = GgufEngineSchema.fromJson(
            config?.modelLoadingParams,
            config?.modelInferenceParams
        )

        val loading = schema.loadingParams
        val inference = schema.inferenceParams
        val compatibility = GgufRuntimeSupport.inspect(File(model.modelPath))

        val success = when (compatibility.backend) {
            GgufRuntimeBackend.SOURCE_AI_CHAT -> {
                loadWithSourceRuntime(model, inference.systemPrompt)
            }

            GgufRuntimeBackend.LEGACY_GGUF_LIB -> {
                try {
                    activeBackend = ActiveBackend.LEGACY
                    engine.load(
                        path = model.modelPath,
                        contextSize = loading.ctxSize,
                        threads = loading.threads,
                        flashAttn = loading.flashAttn,
                        cacheTypeK = cacheTypeIntToString(loading.cacheTypeK),
                        cacheTypeV = cacheTypeIntToString(loading.cacheTypeV)
                    )
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "OOM loading model", e)
                    lastLoadErrorMessage = e.message ?: "Out of memory while loading GGUF model."
                    try { engine.unload() } catch (_: Throwable) {}
                    false
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to load model with legacy GGUF runtime", e)
                    lastLoadErrorMessage = e.message ?: "Legacy GGUF runtime failed to load the model."
                    try { engine.unload() } catch (_: Throwable) {}
                    false
                }
            }
        }

        if (success) {
            currentModelId = model.id

            if (activeBackend == ActiveBackend.LEGACY) {
                engine.setSampling(
                    temperature = inference.temperature,
                    topK = inference.topK,
                    topP = inference.topP,
                    minP = inference.minP,
                    mirostat = inference.mirostat,
                    mirostatTau = inference.mirostatTau,
                    mirostatEta = inference.mirostatEta,
                    seed = inference.seed
                )

                if (inference.systemPrompt.isNotEmpty()) {
                    engine.setSystemPrompt(inference.systemPrompt)
                }
                if (inference.chatTemplate.isNotEmpty()) {
                    engine.setChatTemplate(inference.chatTemplate)
                }
            }
        }

        success
    }

    suspend fun loadFromFd(fd: Int, config: ModelConfig? = null): Boolean = withContext(Dispatchers.IO) {
        if (engine.isLoaded) unload()
        activeBackend = ActiveBackend.LEGACY
        lastLoadErrorMessage = null

        val schema = GgufEngineSchema.fromJson(
            config?.modelLoadingParams,
            config?.modelInferenceParams
        )

        val loading = schema.loadingParams
        val inference = schema.inferenceParams

        val success = try {
            engine.loadFromFd(
                fd = fd,
                contextSize = loading.ctxSize,
                threads = loading.threads,
                flashAttn = loading.flashAttn,
                cacheTypeK = cacheTypeIntToString(loading.cacheTypeK),
                cacheTypeV = cacheTypeIntToString(loading.cacheTypeV)
            )
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM loading model from FD", e)
            lastLoadErrorMessage = e.message ?: "Out of memory while loading GGUF model from file descriptor."
            try { engine.unload() } catch (_: Throwable) {}
            false
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load model from FD with legacy GGUF runtime", e)
            lastLoadErrorMessage = e.message ?: "Legacy GGUF runtime failed to load the model from file descriptor."
            try { engine.unload() } catch (_: Throwable) {}
            false
        }

        if (success) {
            engine.setSampling(
                temperature = inference.temperature,
                topK = inference.topK,
                topP = inference.topP,
                minP = inference.minP,
                mirostat = inference.mirostat,
                mirostatTau = inference.mirostatTau,
                mirostatEta = inference.mirostatEta,
                seed = inference.seed
            )

            currentModelId = "fd_$fd"

            if (inference.systemPrompt.isNotEmpty()) {
                engine.setSystemPrompt(inference.systemPrompt)
            }
            if (inference.chatTemplate.isNotEmpty()) {
                engine.setChatTemplate(inference.chatTemplate)
            }
        }

        success
    }

    fun generateFlow(prompt: String, maxTokens: Int): Flow<GenerationEvent> =
        when (activeBackend) {
            ActiveBackend.LEGACY -> engine.generateFlow(prompt, maxTokens).map { it.toLocal() }
            ActiveBackend.SOURCE -> flow {
                val runtime = sourceRuntime
                if (runtime == null) {
                    emit(GenerationEvent.Error("GGUF runtime не инициализирован."))
                    return@flow
                }
                try {
                    runtime.generate(prompt, maxTokens).collect { token ->
                        if (token.isNotEmpty()) emit(GenerationEvent.Token(token))
                    }
                    emit(GenerationEvent.Done)
                } catch (error: Throwable) {
                    emit(GenerationEvent.Error(error.message ?: "Ошибка генерации GGUF."))
                }
            }
        }

    fun generateMultiTurnFlow(messagesJson: String, maxTokens: Int): Flow<GenerationEvent> =
        when (activeBackend) {
            ActiveBackend.LEGACY -> engine.generateMultiTurnFlow(messagesJson, maxTokens).map { it.toLocal() }
            ActiveBackend.SOURCE -> generateFlow(flattenMessagesToPrompt(messagesJson), maxTokens)
        }

    fun stopGeneration() {
        when (activeBackend) {
            ActiveBackend.LEGACY -> engine.stopGeneration()
            ActiveBackend.SOURCE -> sourceRuntime?.stopGeneration()
        }
    }

    suspend fun unload() = withContext(Dispatchers.IO) {
        when (activeBackend) {
            ActiveBackend.LEGACY -> {
                if (engine.isLoaded) {
                    engine.unload()
                }
            }

            ActiveBackend.SOURCE -> {
                runCatching { sourceRuntime?.unload() }
                sourceRuntime = null
            }
        }

        currentModelId = null
        currentToolsJson = null
        currentToolCallingConfig = null
        lastLoadErrorMessage = null
        activeBackend = ActiveBackend.LEGACY
    }

    fun isModelLoaded(modelId: String): Boolean = isLoaded && currentModelId == modelId

    fun getModelInfo(): String? =
        when (activeBackend) {
            ActiveBackend.LEGACY -> if (engine.isLoaded) engine.getModelInfoJson() else null
            ActiveBackend.SOURCE -> runCatching { runBlocking { sourceRuntime?.getModelInfoJson() } }.getOrNull()
        }

    fun isToolCallingSupported(): Boolean {
        if (activeBackend == ActiveBackend.SOURCE) return false
        if (!engine.isLoaded) return false
        return try {
            engine.isToolCallingSupported()
        } catch (_: Exception) {
            false
        }
    }

    fun enableToolCallingDirect(
        toolDefs: List<ToolDefinitionBuilder>,
        config: ToolCallingConfig
    ): Boolean {
        if (activeBackend == ActiveBackend.SOURCE) return false
        if (!engine.isLoaded) return false

        return try {
            val builtDefs = toolDefs.map { it.build() }
            engine.enableToolCalling(builtDefs, config)
            currentToolCallingConfig = config
            currentToolsJson = null
            Log.d(TAG, "Tool calling enabled: ${builtDefs.size} tools, grammar=${config.grammarMode.name}, typed=${config.useTypedGrammar}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable tool calling", e)
            false
        }
    }

    fun enableToolCalling(
        toolsJson: String,
        grammarMode: Int = GrammarMode.LAZY.value,
        useTypedGrammar: Boolean = true
    ): Boolean {
        if (activeBackend == ActiveBackend.SOURCE) return false
        if (!engine.isLoaded) return false

        return try {
            engine.setToolsJson(toolsJson)
            currentToolsJson = toolsJson
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set tools JSON", e)
            false
        }
    }

    fun setToolsJson(toolsJson: String): Boolean {
        if (activeBackend == ActiveBackend.SOURCE) return false
        if (!engine.isLoaded) return false
        if (toolsJson == currentToolsJson) return true

        return try {
            engine.setToolsJson(toolsJson)
            currentToolsJson = toolsJson
            true
        } catch (_: Exception) {
            false
        }
    }

    fun updateSamplerParams(paramsJson: String): Boolean {
        if (!engine.isLoaded) return false
        return try {
            engine.updateSamplerParams(paramsJson)
        } catch (_: Exception) { false }
    }

    fun setLogitBias(biasJson: String): Boolean {
        if (!engine.isLoaded) return false
        return try {
            engine.setLogitBias(biasJson)
            true
        } catch (_: Exception) { false }
    }

    fun loadControlVectors(vectorsJson: String): Boolean {
        if (!engine.isLoaded) return false
        return try {
            engine.loadControlVectors(vectorsJson)
        } catch (_: Exception) { false }
    }

    fun clearControlVector(): Boolean {
        if (!engine.isLoaded) return false
        return try {
            engine.clearControlVector()
            true
        } catch (_: Exception) { false }
    }

    fun getStateSize(): Long {
        if (!engine.isLoaded) return 0
        return try {
            engine.getStateSize()
        } catch (_: Exception) { 0 }
    }

    fun stateSaveToFile(path: String): Boolean {
        if (!engine.isLoaded) return false
        return try {
            engine.stateSaveToFile(path)
        } catch (_: Exception) { false }
    }

    fun stateLoadFromFile(path: String): Boolean {
        if (!engine.isLoaded) return false
        return try {
            engine.stateLoadFromFile(path)
        } catch (_: Exception) { false }
    }

    fun clearTools() {
        if (engine.isLoaded) {
            try {
                engine.clearTools()
                currentToolsJson = null
                currentToolCallingConfig = null
            } catch (_: Exception) { }
        }
    }

    fun setSpeculativeDecoding(enabled: Boolean, nDraft: Int = 4, ngramSize: Int = 4) {
        if (engine.isLoaded) {
            try {
                engine.setSpeculativeDecoding(enabled, nDraft, ngramSize)
            } catch (_: Exception) { }
        }
    }

    fun setPromptCacheDir(path: String) {
        if (engine.isLoaded) {
            try {
                engine.setPromptCacheDir(path)
            } catch (_: Exception) { }
        }
    }

    fun warmUp(): Boolean {
        if (!engine.isLoaded) return false
        return try {
            engine.warmUp()
        } catch (_: Exception) { false }
    }

    fun supportsThinking(): Boolean {
        if (!engine.isLoaded) return false
        return try {
            engine.supportsThinking()
        } catch (_: Exception) { false }
    }

    fun setThinkingEnabled(enabled: Boolean) {
        if (engine.isLoaded) {
            try {
                engine.setThinkingEnabled(enabled)
            } catch (_: Exception) { }
        }
    }

    fun getContextUsage(): Float {
        if (!engine.isLoaded) return 0f
        return try {
            engine.getContextUsage()
        } catch (_: Exception) { 0f }
    }

    fun getContextInfo(prompt: String? = null): com.dark.gguf_lib.ContextInfo {
        if (!engine.isLoaded) return com.dark.gguf_lib.ContextInfo(0, 0, 0, -1, -1)
        return try {
            engine.getContextInfo(prompt)
        } catch (_: Exception) { com.dark.gguf_lib.ContextInfo(0, 0, 0, -1, -1) }
    }

    private val characterEngine by lazy { com.dark.gguf_lib.CharacterEngine(engine) }

    fun setPersonality(personalityJson: String): Boolean {
        if (!engine.isLoaded) return false
        return try {
            val j = org.json.JSONObject(personalityJson)
            characterEngine.setPersonality(com.dark.gguf_lib.Personality(
                name = j.optString("name", ""),
                persona = j.optString("persona", ""),
                temperature = j.optDouble("temperature", 0.7).toFloat(),
                topP = j.optDouble("topP", 0.9).toFloat(),
                repetitionPenalty = j.optDouble("repetitionPenalty", 1.1).toFloat(),
                creativity = j.optDouble("creativity", 0.5).toFloat(),
                verbosity = j.optDouble("verbosity", 0.5).toFloat(),
                formality = j.optDouble("formality", 0.5).toFloat(),
                topK = j.optInt("topK", -1),
                minP = j.optDouble("minP", -1.0).toFloat(),
            ))
            true
        } catch (_: Exception) { false }
    }

    fun setMood(mood: Int): Boolean {
        if (!engine.isLoaded) return false
        return try {
            characterEngine.setMood(com.dark.gguf_lib.Mood.entries[mood])
            true
        } catch (_: Exception) { false }
    }

    fun setCustomMood(tempMod: Float, topPMod: Float, repPenaltyMod: Float): Boolean {
        if (!engine.isLoaded) return false
        return try {
            characterEngine.setCustomMood(tempMod, topPMod, repPenaltyMod)
            true
        } catch (_: Exception) { false }
    }

    fun getCharacterContext(): String {
        if (!engine.isLoaded) return ""
        return try {
            characterEngine.getContext()
        } catch (_: Exception) { "" }
    }

    fun buildPrompt(userPrompt: String): String {
        if (!engine.isLoaded) return userPrompt
        return try {
            characterEngine.buildPrompt(userPrompt)
        } catch (_: Exception) { userPrompt }
    }

    fun setUncensored(enabled: Boolean): Boolean {
        if (!engine.isLoaded) return false
        return try {
            characterEngine.setUncensored(enabled)
            true
        } catch (_: Exception) { false }
    }

    fun isUncensored(): Boolean {
        if (!engine.isLoaded) return false
        return try {
            characterEngine.isUncensored
        } catch (_: Exception) { false }
    }

    fun calcVectors(prompt: String, onProgress: ((Float) -> Unit)? = null): FloatArray? {
        if (!engine.isLoaded) return null
        return try {
            characterEngine.calcVectors(prompt, onProgress)
        } catch (_: Exception) { null }
    }

    fun applyVectors(data: FloatArray, strength: Float = 1.0f, ilStart: Int = -1, ilEnd: Int = -1): Boolean {
        if (!engine.isLoaded) return false
        return try {
            characterEngine.applyVectors(data, strength, ilStart, ilEnd)
        } catch (_: Exception) { false }
    }

    fun clearVectors(): Boolean {
        if (!engine.isLoaded) return false
        return try {
            characterEngine.clearVectors()
            true
        } catch (_: Exception) { false }
    }

    fun loadVlmProjector(path: String, threads: Int = 0): Boolean {
        if (!engine.isLoaded) return false
        return try {
            engine.loadVlmProjector(path, threads)
        } catch (_: Exception) { false }
    }

    fun loadVlmProjectorFromFd(fd: Int, threads: Int = 0): Boolean {
        if (!engine.isLoaded) return false
        return try {
            engine.loadVlmProjectorFromFd(fd, threads)
        } catch (_: Exception) { false }
    }

    fun releaseVlmProjector() {
        try { engine.releaseVlmProjector() } catch (_: Exception) { }
    }

    val isVlmLoaded: Boolean get() = engine.isVlmLoaded

    fun getVlmDefaultMarker(): String = engine.getVlmDefaultMarker()

    fun generateVlmFlow(
        messagesJson: String,
        imageData: List<ByteArray>,
        maxTokens: Int
    ): Flow<GenerationEvent> =
        engine.generateVlmFlow(messagesJson, imageData, maxTokens).map { it.toLocal() }

    private suspend fun loadWithSourceRuntime(model: Model, systemPrompt: String): Boolean {
        return try {
            val runtime = sourceRuntime ?: SourceDistributedRuntime.create(AppContainer.getAppContext()).also {
                sourceRuntime = it
            }
            runtime.state.first { state ->
                state !is InferenceEngine.State.Uninitialized &&
                    state !is InferenceEngine.State.Initializing
            }
            runtime.loadModel(model.modelPath, systemPrompt.takeIf { it.isNotBlank() })
            activeBackend = ActiveBackend.SOURCE
            true
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to load model with source runtime", error)
            lastLoadErrorMessage = error.message ?: "Source GGUF runtime failed to load the model."
            sourceRuntime = null
            activeBackend = ActiveBackend.LEGACY
            false
        }
    }

    private fun flattenMessagesToPrompt(messagesJson: String): String {
        return runCatching {
            val array = JSONArray(messagesJson)
            buildString {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val role = item.optString("role").ifBlank { "user" }
                    val content = item.optString("content")
                    if (content.isBlank()) continue
                    append('[')
                    append(role.uppercase())
                    append("]\n")
                    append(content)
                    append("\n\n")
                }
            }.trim()
        }.getOrElse { messagesJson }
    }

    companion object {
        private const val TAG = "GGUFEngine"

        fun getRecommendedParams(context: Context): GgufLoadingParams {
            val profile = HardwareScanner.scan(context)
            return DeviceTuner.tune(profile, modelSizeMB = 0, mode = com.santiya.localaihub.global.PerformanceMode.BALANCED)
        }

        fun getRecommendedContextSize(context: Context, modelSizeMB: Int, modelName: String = ""): Int {
            return DeviceTuner.recommendContextSize(context, modelSizeMB, modelName)
        }

        private fun cacheTypeIntToString(type: Int): String = when (type) {
            0 -> "f32"
            1 -> "f16"
            8 -> "q5_1"
            9 -> "q8_0"
            10 -> "q4_0"
            11 -> "q4_1"
            12 -> "q5_0"
            else -> "q8_0"
        }
    }
}

sealed class GenerationEvent {
    data class Token(val text: String) : GenerationEvent()
    data class ToolCall(val name: String, val args: String) : GenerationEvent()
    data object Done : GenerationEvent()
    data class Error(val message: String) : GenerationEvent()
    data class Metrics(val metrics: DecodingMetrics) : GenerationEvent()
    data class Progress(val progress: Float) : GenerationEvent()
}

private fun LibGenerationEvent.toLocal(): GenerationEvent = when (this) {
    is LibGenerationEvent.Token -> GenerationEvent.Token(text)
    is LibGenerationEvent.ToolCall -> GenerationEvent.ToolCall(name, argsJson)
    is LibGenerationEvent.Done -> GenerationEvent.Done
    is LibGenerationEvent.Error -> GenerationEvent.Error(message)
    is LibGenerationEvent.Metrics -> GenerationEvent.Metrics(metrics.toLocal())
    is LibGenerationEvent.Progress -> GenerationEvent.Progress(progress)
}
