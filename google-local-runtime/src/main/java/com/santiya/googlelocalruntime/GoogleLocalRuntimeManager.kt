package com.santiya.googlelocalruntime

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class GoogleLocalRuntimeManager(
    private val context: Context,
) {

    private var currentDescriptor: GoogleLocalModelDescriptor? = null
    private var litertEngine: Engine? = null
    private var litertConversation: Conversation? = null
    private var aicoreModel: GenerativeModel? = null

    val currentModelId: String?
        get() = currentDescriptor?.modelId

    val currentRuntimeType: GoogleLocalRuntimeType?
        get() = currentDescriptor?.runtimeType

    fun isLoaded(modelId: String? = null): Boolean {
        val descriptor = currentDescriptor ?: return false
        if (modelId != null && descriptor.modelId != modelId) return false
        return when (descriptor.runtimeType) {
            GoogleLocalRuntimeType.LITERT_LM -> litertEngine != null && litertConversation != null
            GoogleLocalRuntimeType.AICORE -> aicoreModel != null
        }
    }

    suspend fun load(descriptor: GoogleLocalModelDescriptor): Result<String> {
        unload()
        return runCatching {
            when (descriptor.runtimeType) {
                GoogleLocalRuntimeType.LITERT_LM -> loadLiteRt(descriptor)
                GoogleLocalRuntimeType.AICORE -> loadAicore(descriptor)
            }
            currentDescriptor = descriptor
            descriptor.modelName
        }
    }

    suspend fun unload() {
        runCatching { litertConversation?.close() }
        runCatching { litertEngine?.close() }
        runCatching { aicoreModel?.close() }
        litertConversation = null
        litertEngine = null
        aicoreModel = null
        currentDescriptor = null
    }

    fun stopGeneration() {
        runCatching { litertConversation?.cancelProcess() }
    }

    fun supportsToolCalling(): Boolean = false

    suspend fun getAicoreAvailability(descriptor: GoogleLocalModelDescriptor): GoogleLocalAvailabilityState {
        if (descriptor.runtimeType != GoogleLocalRuntimeType.AICORE) return GoogleLocalAvailabilityState.UNAVAILABLE
        return runCatching {
            when (buildAicoreClient(descriptor).checkStatus()) {
                FeatureStatus.AVAILABLE -> GoogleLocalAvailabilityState.AVAILABLE
                FeatureStatus.DOWNLOADABLE -> GoogleLocalAvailabilityState.DOWNLOADABLE
                FeatureStatus.DOWNLOADING -> GoogleLocalAvailabilityState.DOWNLOADING
                FeatureStatus.UNAVAILABLE -> GoogleLocalAvailabilityState.UNAVAILABLE
                else -> GoogleLocalAvailabilityState.UNKNOWN
            }
        }.getOrElse {
            Log.w(TAG, "AICore availability check failed", it)
            GoogleLocalAvailabilityState.UNAVAILABLE
        }
    }

    fun downloadAicoreModel(
        descriptor: GoogleLocalModelDescriptor,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit,
    ): Flow<GoogleLocalAvailabilityState> = callbackFlow {
        if (descriptor.runtimeType != GoogleLocalRuntimeType.AICORE) {
            trySend(GoogleLocalAvailabilityState.UNAVAILABLE)
            close()
            return@callbackFlow
        }
        val model = buildAicoreClient(descriptor)
        launch {
            try {
                when (model.checkStatus()) {
                    FeatureStatus.AVAILABLE -> {
                        onComplete()
                        trySend(GoogleLocalAvailabilityState.AVAILABLE)
                        close()
                    }
                    FeatureStatus.DOWNLOADABLE,
                    FeatureStatus.DOWNLOADING -> {
                        model.download().collect { status ->
                            when (status) {
                                is DownloadStatus.DownloadStarted -> {
                                    onProgress(0L, status.bytesToDownload)
                                    trySend(GoogleLocalAvailabilityState.DOWNLOADING)
                                }
                                is DownloadStatus.DownloadProgress -> {
                                    onProgress(status.totalBytesDownloaded, 0L)
                                    trySend(GoogleLocalAvailabilityState.DOWNLOADING)
                                }
                                is DownloadStatus.DownloadCompleted -> {
                                    onComplete()
                                    trySend(GoogleLocalAvailabilityState.AVAILABLE)
                                    close()
                                }
                                is DownloadStatus.DownloadFailed -> {
                                    onError(status.e.message ?: "AICore download failed")
                                    trySend(GoogleLocalAvailabilityState.UNAVAILABLE)
                                    close()
                                }
                            }
                        }
                    }
                    FeatureStatus.UNAVAILABLE -> {
                        onError("AICore недоступен на этом устройстве.")
                        trySend(GoogleLocalAvailabilityState.UNAVAILABLE)
                        close()
                    }
                    else -> {
                        onError("Не удалось определить состояние AICore.")
                        trySend(GoogleLocalAvailabilityState.UNKNOWN)
                        close()
                    }
                }
            } catch (error: Exception) {
                onError(error.message ?: "AICore download failed")
                trySend(GoogleLocalAvailabilityState.UNAVAILABLE)
                close()
            }
        }
        awaitClose { }
    }.flowOn(Dispatchers.IO)

    fun generateFlow(messages: List<GoogleLocalMessage>): Flow<GoogleLocalGenerationEvent> = callbackFlow {
        val descriptor = currentDescriptor
        if (descriptor == null) {
            trySend(GoogleLocalGenerationEvent.Error("Google Local runtime is not loaded."))
            close()
            return@callbackFlow
        }
        when (descriptor.runtimeType) {
            GoogleLocalRuntimeType.LITERT_LM -> {
                val engine = litertEngine
                if (engine == null) {
                    trySend(GoogleLocalGenerationEvent.Error("LiteRT runtime is not initialized."))
                    close()
                    return@callbackFlow
                }
                val conversation = resetLiteRtConversation(engine, descriptor)
                litertConversation = conversation
                val prompt = buildTranscriptPrompt(messages)
                val closed = AtomicBoolean(false)
                conversation.sendMessageAsync(
                    Contents.of(listOf(Content.Text(prompt))),
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            trySend(GoogleLocalGenerationEvent.Token(message.toString()))
                        }

                        override fun onDone() {
                            if (closed.compareAndSet(false, true)) {
                                trySend(GoogleLocalGenerationEvent.Done)
                                close()
                            }
                        }

                        override fun onError(throwable: Throwable) {
                            if (closed.compareAndSet(false, true)) {
                                val message = if (throwable is CancellationException) {
                                    "Генерация остановлена."
                                } else {
                                    throwable.message ?: "LiteRT generation failed"
                                }
                                trySend(GoogleLocalGenerationEvent.Error(message))
                                close()
                            }
                        }
                    },
                    emptyMap(),
                )
            }

            GoogleLocalRuntimeType.AICORE -> {
                val model = aicoreModel
                if (model == null) {
                    trySend(GoogleLocalGenerationEvent.Error("AICore runtime is not initialized."))
                    close()
                    return@callbackFlow
                }
                launch {
                    try {
                        val prompt = buildTranscriptPrompt(messages)
                        val request = generateContentRequest(TextPart(prompt)) {
                            this.temperature = descriptor.temperature.coerceIn(0.0f, 1.0f)
                            this.topK = descriptor.topK
                        }
                        model.generateContentStream(request).collect { response ->
                            val candidate = response.candidates.firstOrNull()
                            val text = candidate?.text.orEmpty()
                            if (text.isNotEmpty()) {
                                trySend(GoogleLocalGenerationEvent.Token(text))
                            }
                            if (candidate?.finishReason != null) {
                                trySend(GoogleLocalGenerationEvent.Done)
                                close()
                            }
                        }
                    } catch (error: CancellationException) {
                        trySend(GoogleLocalGenerationEvent.Done)
                        close()
                    } catch (error: Exception) {
                        trySend(GoogleLocalGenerationEvent.Error(error.message ?: "AICore generation failed"))
                        close()
                    }
                }
            }
        }

        awaitClose { }
    }.flowOn(Dispatchers.IO)

    private fun loadLiteRt(descriptor: GoogleLocalModelDescriptor) {
        val modelPath = descriptor.localPath?.takeIf { it.isNotBlank() }
            ?: error("Missing LiteRT-LM model path")
        GoogleLocalModelValidator.validateLiteRtModel(java.io.File(modelPath)).getOrThrow()
        val engine = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = resolveBackend(descriptor.accelerator),
                maxNumTokens = descriptor.maxTokens,
                cacheDir = context.cacheDir.absolutePath,
            )
        )
        engine.initialize()
        litertEngine = engine
        litertConversation = resetLiteRtConversation(engine, descriptor)
    }

    private suspend fun loadAicore(descriptor: GoogleLocalModelDescriptor) {
        val model = buildAicoreClient(descriptor)
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> {
                runCatching { model.warmup() }
                aicoreModel = model
            }
            FeatureStatus.DOWNLOADABLE,
            FeatureStatus.DOWNLOADING -> error("AICore model must be downloaded inside the hub before loading.")
            FeatureStatus.UNAVAILABLE -> error("AICore недоступен на этом устройстве.")
            else -> error("Unknown AICore state.")
        }
    }

    private fun buildAicoreClient(descriptor: GoogleLocalModelDescriptor): GenerativeModel {
        return Generation.getClient(
            generationConfig {
                modelConfig = modelConfig {
                    releaseStage = if (descriptor.aicoreReleaseStage.equals("preview", ignoreCase = true)) {
                        ModelReleaseStage.PREVIEW
                    } else {
                        ModelReleaseStage.STABLE
                    }
                    preference = if (descriptor.aicorePreference.equals("full", ignoreCase = true)) {
                        ModelPreference.FULL
                    } else {
                        ModelPreference.FAST
                    }
                }
            }
        )
    }

    private fun resolveBackend(accelerator: String): Backend = when (accelerator.lowercase()) {
        "gpu" -> Backend.GPU()
        "npu", "tpu" -> Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
        else -> Backend.CPU()
    }

    private fun resetLiteRtConversation(
        engine: Engine,
        descriptor: GoogleLocalModelDescriptor,
    ): Conversation {
        runCatching { litertConversation?.close() }
        return engine.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(
                    topK = descriptor.topK,
                    topP = descriptor.topP.toDouble(),
                    temperature = descriptor.temperature.toDouble(),
                )
            )
        )
    }

    private fun buildTranscriptPrompt(messages: List<GoogleLocalMessage>): String {
        val transcript = messages.joinToString(separator = "\n") { message ->
            "${message.role}: ${message.content.trim()}"
        }
        return "$transcript\nassistant:"
    }

    companion object {
        private const val TAG = "GoogleLocalRuntime"
    }
}
