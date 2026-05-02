package com.santiya.localaihub.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.FaceDetector
import android.os.Build
import android.os.IBinder
import android.os.DeadObjectException
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.santiya.localaihub.R
import com.santiya.localaihub.api.LocalHttpApiController
import com.santiya.localaihub.catalog.CatalogSearchFilters
import com.santiya.localaihub.catalog.CatalogSource
import com.santiya.localaihub.catalog.UniversalCatalogRepository
import com.santiya.localaihub.data.AppSettingsDataStore
import com.santiya.localaihub.di.AppContainer
import com.santiya.localaihub.engine.DiffusionEngine
import com.santiya.localaihub.engine.GGUFEngine
import com.santiya.localaihub.engine.GenerationEvent
import com.santiya.localaihub.hub.ExternalAccessManager
import com.santiya.localaihub.hub.LanCoordinator
import com.santiya.localaihub.hub.ModelOrchestraManager
import com.santiya.localaihub.hub.OrchestraConfig
import com.santiya.localaihub.models.data.HFModelRepository
import com.santiya.localaihub.models.data.HuggingFaceModel
import com.santiya.localaihub.models.data.ModelType
import com.santiya.localaihub.models.enums.PathType
import com.santiya.localaihub.models.enums.ProviderType
import com.santiya.localaihub.models.table_schema.Model
import com.santiya.localaihub.models.table_schema.ModelConfig
import com.santiya.localaihub.repo.ModelRepositoryDataStore
import com.santiya.localaihub.repo.ModelStoreRepository
import com.santiya.localaihub.runtime.ModelRegistry
import com.santiya.localaihub.state.AppStateManager
import java.io.File
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

class LLMService : Service() {

    companion object {
        private const val TAG = "LLMService"
        const val ACTION_WAKE_HUB = "com.santiya.localaihub.service.action.WAKE_HUB"

        /** Direct reference for same-process callers (avoids AIDL serialization). */
        @Volatile
        var instance: LLMService? = null
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val ggufEngine = GGUFEngine()
    private val diffusionEngine = DiffusionEngine()
    private val modelRegistry = ModelRegistry()
    private val httpApiController = LocalHttpApiController()
    private val appSettings by lazy { AppSettingsDataStore(applicationContext) }
    private val externalAccessManager by lazy { ExternalAccessManager(applicationContext) }
    private val lanCoordinator by lazy { LanCoordinator(applicationContext) }
    private val orchestraManager by lazy { ModelOrchestraManager(applicationContext) }
    private val serviceJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val modelStoreRepository by lazy { ModelStoreRepository(applicationContext) }
    private val repositoryDataStore by lazy { ModelRepositoryDataStore(applicationContext) }
    private val universalCatalogRepository by lazy { UniversalCatalogRepository() }

    fun publicCatalogJson(locale: String?): String = externalCatalogJson(locale)

    private fun installedModelsSnapshot(): List<Model> = runCatching {
        runBlocking { AppContainer.getModelRepository().getAllModels().first() }
    }.getOrDefault(emptyList())

    private fun externalAccessErrorJson(packageName: String?): String {
        val safePackage = packageName ?: "unknown"
        return """{"ok":false,"error":"external_access_disabled","package":${org.json.JSONObject.quote(safePackage)},"message":"Р”РѕСЃС‚СѓРї Рє AI РґР»СЏ СЌС‚РѕРіРѕ РїСЂРёР»РѕР¶РµРЅРёСЏ РЅРµ СЂР°Р·СЂРµС€С‘РЅ."}"""
    }

    private fun gateExternalJson(block: () -> String): String {
        val caller = externalAccessManager.resolveCallingPackage()
        if (caller != null && caller != packageName && !externalAccessManager.isPackageAllowed(caller)) {
            runBlocking { externalAccessManager.recordPending(caller) }
            return externalAccessErrorJson(caller)
        }
        return block()
    }

    private fun authErrorJson(result: com.santiya.localaihub.hub.ExternalAuthResult): String {
        return JSONObject()
            .put("ok", false)
            .put("error", result.message)
            .put("packageName", result.packageName ?: "unknown")
            .put(
                "message",
                when (result.message) {
                    "external_access_disabled" -> "Внешний доступ к Hub выключен."
                    "client_not_approved" -> "Приложение ещё не одобрено в настройках Hub."
                    "invalid_credentials" -> "Неверные clientId или apiKey."
                    "package_mismatch" -> "packageName в запросе не совпадает с реальным клиентом."
                    "client_credentials_missing" -> "Клиент зарегистрирован, но credentials ещё не выданы."
                    else -> "Внешний вызов отклонён."
                }
            )
            .toString()
    }
    private inline fun gateAuthenticatedJson(
        auth: HubAuthEnvelope?,
        block: (packageName: String?) -> String,
    ): String {
        val authResult = externalAccessManager.validateAuth(auth)
        if (!authResult.ok) {
            authResult.packageName?.let { pkg ->
                runBlocking { externalAccessManager.recordPending(pkg) }
            }
            return authErrorJson(authResult)
        }
        return block(authResult.packageName)
    }

    private fun availableStoreModels(): List<HuggingFaceModel> {
        val repositories: List<HFModelRepository> = runBlocking { repositoryDataStore.repositories.first() }
        return runBlocking {
            modelStoreRepository.getAvailableModels(repositories).getOrDefault(emptyList())
        }.sortedBy { parseApproximateSizeBytes(it.approximateSize) }
    }

    private fun availableChatStoreModels(): List<HuggingFaceModel> {
        return availableStoreModels()
            .filter { it.modelType == ModelType.GGUF }
    }

    private fun externalCatalogJson(locale: String?): String =
        serviceJson.encodeToString(
            availableStoreModels().map { it.toExternalCatalogDescriptor(locale) },
        )

    private fun searchCatalog(request: CatalogSearchRequest): List<HuggingFaceModel> {
        val repositories: List<HFModelRepository> = runBlocking { repositoryDataStore.repositories.first() }
        val curated = runBlocking {
            modelStoreRepository.getAvailableModels(repositories).getOrDefault(emptyList())
        }
        val filters = CatalogSearchFilters(
            sources = request.sources.mapNotNull(CatalogSource::fromWireName).toSet().ifEmpty { CatalogSource.entries.toSet() },
            capabilities = request.capabilities.toSet(),
            familyTags = request.familyTags.toSet(),
        )
        return runBlocking {
            universalCatalogRepository.search(
                query = request.query,
                filters = filters,
                curatedModels = curated,
                repositories = repositories
            )
        }
    }

    private fun catalogModelById(modelId: String): HuggingFaceModel? {
        val repositories: List<HFModelRepository> = runBlocking { repositoryDataStore.repositories.first() }
        val curated = runBlocking {
            modelStoreRepository.getAvailableModels(repositories).getOrDefault(emptyList())
        }
        return runBlocking {
            universalCatalogRepository.search(
                query = "",
                filters = CatalogSearchFilters(),
                curatedModels = curated,
                repositories = repositories
            )
        }.firstOrNull { it.id == modelId }
    }

    private fun resolvePreferredModelId(capability: String): String? {
        val preferred = runBlocking { appSettings.preferredModelsSnapshot() }
        return when (capability) {
            "chat" -> preferred.chatModelId
            "vision" -> preferred.visionModelId
            "image_generation" -> preferred.imageGenerationModelId
            "video_generation" -> preferred.videoGenerationModelId
            "tts" -> preferred.ttsModelId
            "files" -> preferred.filesModelId
            "assistant_live" -> preferred.assistantLiveModelId
            else -> null
        }
    }

    private fun startHubModelDownload(model: HuggingFaceModel) {
        val fileUrl = model.downloadUrlOverride
            ?: model.fileUri.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: model.fileUri.takeIf { it.isNotBlank() }?.let { "https://huggingface.co/$it" }
            ?: return
        val serviceModelType = when {
            model.downloadability == com.santiya.localaihub.hub.Downloadability.RAW_ASSET_DOWNLOAD &&
                model.capabilities.any { it.equals("tts", ignoreCase = true) } -> "TTS_PIPER"
            model.resolvedFileName?.endsWith(".onnx", ignoreCase = true) == true ||
                model.capabilities.any {
                    it.equals("object_detection", ignoreCase = true) ||
                        it.equals("face_detection", ignoreCase = true) ||
                        it.equals("face_recognition", ignoreCase = true)
                } -> "ONNX"
            model.downloadability == com.santiya.localaihub.hub.Downloadability.RAW_ASSET_DOWNLOAD || model.rawAssetOnly -> "RAW_ASSET"
            else -> model.modelType.name
        }
        val intent = Intent(applicationContext, ModelDownloadService::class.java).apply {
            action = ModelDownloadService.ACTION_START_DOWNLOAD
            putExtra(ModelDownloadService.EXTRA_MODEL_ID, model.id)
            putExtra(ModelDownloadService.EXTRA_MODEL_NAME, model.name)
            putExtra(ModelDownloadService.EXTRA_FILE_URL, fileUrl)
            putExtra(ModelDownloadService.EXTRA_IS_ZIP, model.isZip)
            putExtra(ModelDownloadService.EXTRA_MODEL_TYPE, serviceModelType)
            putExtra(ModelDownloadService.EXTRA_RUN_ON_CPU, model.runOnCpu)
            putExtra(ModelDownloadService.EXTRA_TEXT_EMBEDDING_SIZE, model.textEmbeddingSize)
        }
        androidx.core.content.ContextCompat.startForegroundService(applicationContext, intent)
    }

    private fun preparePreferredChatModel(): ExternalPreparePreferredResponse {
        val preferredModelId = resolvePreferredModelId("chat")
            ?: return ExternalPreparePreferredResponse(
                ok = false,
                message = "No preferred chat model selected in AI Hub.",
            )
        val repository = AppContainer.getModelRepository()
        val model = runBlocking { repository.getModelById(preferredModelId) }
            ?: return ExternalPreparePreferredResponse(
                ok = false,
                modelId = preferredModelId,
                runtime = "gguf",
                message = "Preferred chat model is not installed in AI Hub.",
            )
        if (model.providerType != ProviderType.GGUF) {
            return ExternalPreparePreferredResponse(
                ok = false,
                modelId = model.id,
                runtime = model.providerType.name.lowercase(),
                message = "Preferred model is not a GGUF chat model.",
            )
        }
        val config = runBlocking { repository.getConfigByModelId(model.id) }
            ?: return ExternalPreparePreferredResponse(
                ok = false,
                modelId = model.id,
                runtime = "gguf",
                message = "Missing model config in AI Hub.",
            )

        return runBlocking(Dispatchers.IO) {
            if (ggufEngine.isModelLoaded(model.id)) {
                ExternalPreparePreferredResponse(
                    ok = true,
                    modelId = model.id,
                    runtime = "gguf",
                    message = "Preferred chat model is already loaded in AI Hub.",
                )
            } else {
                AppStateManager.setLoadingModel(model.modelName)
                val success = ggufEngine.load(model, config)
                if (success) {
                    AppStateManager.setModelLoaded(model.modelName)
                    ExternalPreparePreferredResponse(
                        ok = true,
                        modelId = model.id,
                        runtime = "gguf",
                        message = "Preferred chat model prepared in AI Hub.",
                    )
                } else {
                    AppStateManager.setError("Failed to load preferred chat model: ${model.modelName}")
                    ExternalPreparePreferredResponse(
                        ok = false,
                        modelId = model.id,
                        runtime = "gguf",
                        message = "AI Hub failed to load the preferred chat model.",
                    )
                }
            }
        }
    }

    private fun prepareChatModel(modelId: String?): ExternalPreparePreferredResponse {
        val resolvedModelId = modelId?.takeIf { it.isNotBlank() } ?: resolvePreferredModelId("chat")
            ?: return ExternalPreparePreferredResponse(
                ok = false,
                message = "No chat model specified and no preferred chat model selected.",
            )
        val repository = AppContainer.getModelRepository()
        val model = runBlocking { repository.getModelById(resolvedModelId) }
            ?: return ExternalPreparePreferredResponse(
                ok = false,
                modelId = resolvedModelId,
                message = "Requested chat model is not installed in AI Hub.",
            )
        if (model.providerType != ProviderType.GGUF) {
            return ExternalPreparePreferredResponse(
                ok = false,
                modelId = resolvedModelId,
                runtime = model.providerType.name.lowercase(),
                message = "Requested model is not a GGUF chat model.",
            )
        }
        val config = runBlocking { repository.getConfigByModelId(model.id) }
            ?: return ExternalPreparePreferredResponse(
                ok = false,
                modelId = model.id,
                runtime = "gguf",
                message = "Missing model config in AI Hub.",
            )

        return runBlocking(Dispatchers.IO) {
            if (ggufEngine.isModelLoaded(model.id)) {
                ExternalPreparePreferredResponse(
                    ok = true,
                    modelId = model.id,
                    runtime = "gguf",
                    message = "Chat model is already loaded in AI Hub.",
                )
            } else {
                AppStateManager.setLoadingModel(model.modelName)
                val success = ggufEngine.load(model, config)
                if (success) {
                    AppStateManager.setModelLoaded(model.modelName)
                    ExternalPreparePreferredResponse(
                        ok = true,
                        modelId = model.id,
                        runtime = "gguf",
                        message = "Chat model prepared in AI Hub.",
                    )
                } else {
                    ExternalPreparePreferredResponse(
                        ok = false,
                        modelId = model.id,
                        runtime = "gguf",
                        message = "AI Hub failed to load the requested chat model.",
                    )
                }
            }
        }
    }

    private fun executeChatRequest(request: HubExecutionRequest): String {
        val prepare = prepareChatModel(request.modelId)
        if (!prepare.ok) return serviceJson.encodeToString(prepare)

        val inputJson = runCatching { JSONObject(request.inputJson.ifBlank { "{}" }) }.getOrDefault(JSONObject())
        val userPrompt = inputJson.optString("prompt").ifBlank {
            inputJson.optString("text")
        }
        if (userPrompt.isBlank()) {
            return """{"ok":false,"status":"invalid_request","message":"Prompt is required in inputJson."}"""
        }
        val wrappedPrompt = request.systemPrompt?.takeIf { it.isNotBlank() }?.let {
            "[SYSTEM]\n$it\n\n[USER]\n$userPrompt"
        } ?: userPrompt
        val responseBuilder = StringBuilder()
        runBlocking {
            ggufEngine.generateFlow(wrappedPrompt, inputJson.optInt("maxTokens", 512)).collect { event ->
                when (event) {
                    is GenerationEvent.Token -> responseBuilder.append(event.text)
                    is GenerationEvent.Error -> throw IllegalStateException(event.message)
                    else -> Unit
                }
            }
        }
        return JSONObject()
            .put("ok", true)
            .put("mode", request.orchestrationMode)
            .put("modelId", prepare.modelId ?: "")
            .put("response", responseBuilder.toString().trim())
            .toString()
    }

    private fun executeVisionPrompt(prompt: String, imageBase64: String): String {
        if (!ggufEngine.isLoaded || !ggufEngine.isVlmLoaded) {
            return """{"ok":false,"status":"vlm_not_ready","message":"Load a text model and mmproj projector before VLM image reasoning."}"""
        }
        val imageBytes = runCatching {
            Base64.decode(imageBase64.substringAfter(","), Base64.DEFAULT)
        }.getOrElse {
            return """{"ok":false,"status":"invalid_image","message":"Image must be a valid base64-encoded frame."}"""
        }
        val marker = ggufEngine.getVlmDefaultMarker()
        val messagesJson = JSONArray(
            listOf(
                JSONObject().put("role", "user").put("content", "$marker\n$prompt")
            )
        ).toString()
        val resultBuilder = StringBuilder()
        runBlocking {
            ggufEngine.generateVlmFlow(messagesJson, listOf(imageBytes), 256).collect { event ->
                when (event) {
                    is GenerationEvent.Token -> resultBuilder.append(event.text)
                    is GenerationEvent.Error -> throw IllegalStateException(event.message)
                    else -> Unit
                }
            }
        }
        return JSONObject().put("ok", true).put("status", "completed").put("response", resultBuilder.toString().trim()).toString()
    }

    private data class DetectedFaceBox(
        val centerX: Float,
        val centerY: Float,
        val width: Float,
        val height: Float,
        val confidence: Float,
    )

    private fun decodeBase64Bitmap(imageBase64: String): Bitmap? {
        val raw = imageBase64.substringAfter(",", imageBase64)
        val bytes = runCatching { Base64.decode(raw, Base64.DEFAULT) }.getOrNull() ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun detectFaces(bitmap: Bitmap, maxFaces: Int = 16): List<DetectedFaceBox> {
        val safeBitmap = if (bitmap.config == Bitmap.Config.RGB_565) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.RGB_565, true)
        } ?: return emptyList()
        val detector = FaceDetector(safeBitmap.width, safeBitmap.height, maxFaces)
        val faces = arrayOfNulls<FaceDetector.Face>(maxFaces)
        val detectedCount = runCatching { detector.findFaces(safeBitmap, faces) }.getOrDefault(0)
        val midPoint = android.graphics.PointF()
        return buildList {
            for (index in 0 until detectedCount) {
                val face = faces[index] ?: continue
                face.getMidPoint(midPoint)
                val eyesDistance = face.eyesDistance()
                add(
                    DetectedFaceBox(
                        centerX = midPoint.x,
                        centerY = midPoint.y,
                        width = eyesDistance * 2.8f,
                        height = eyesDistance * 3.4f,
                        confidence = face.confidence(),
                    )
                )
            }
        }
    }

    private fun extractImageBase64(inputJson: JSONObject, fallback: String? = null): String? {
        return inputJson.optString("imageBase64").takeIf { it.isNotBlank() }
            ?: inputJson.optString("image").takeIf { it.isNotBlank() }
            ?: inputJson.optString("frameBase64").takeIf { it.isNotBlank() }
            ?: fallback?.takeIf { it.isNotBlank() }
    }

    private fun buildVisionPrompt(capability: String, inputJson: JSONObject): String {
        val explicitPrompt = inputJson.optString("prompt").ifBlank { inputJson.optString("question") }
        if (explicitPrompt.isNotBlank()) return explicitPrompt
        return when (capability) {
            "object_detection" -> "РљСЂР°С‚РєРѕ РїРµСЂРµС‡РёСЃР»Рё РѕР±СЉРµРєС‚С‹ РІ РєР°РґСЂРµ Рё РёС… РїСЂРёРјРµСЂРЅРѕРµ РїРѕР»РѕР¶РµРЅРёРµ."
            "text", "ocr" -> "РР·РІР»РµРєРё С‡РёС‚Р°РµРјС‹Р№ С‚РµРєСЃС‚ СЃ РёР·РѕР±СЂР°Р¶РµРЅРёСЏ Рё РІРµСЂРЅРё РµРіРѕ Р±РµР· Р»РёС€РЅРёС… РїРѕСЏСЃРЅРµРЅРёР№."
            "assistant_live" -> "РћРїРёС€Рё СЃС†РµРЅСѓ РїРµСЂРµРґ РєР°РјРµСЂРѕР№ Рё РїСЂРµРґР»РѕР¶Рё СЃР»РµРґСѓСЋС‰РµРµ РїРѕР»РµР·РЅРѕРµ РґРµР№СЃС‚РІРёРµ."
            else -> "РћРїРёС€Рё, С‡С‚Рѕ РёР·РѕР±СЂР°Р¶РµРЅРѕ РЅР° РєР°РґСЂРµ, РєСЂР°С‚РєРѕ Рё РїРѕ РґРµР»Сѓ."
        }
    }

    private fun detectFacesJson(imageBase64: String, matchingRequested: Boolean): String {
        val bitmap = decodeBase64Bitmap(imageBase64)
            ?: return """{"ok":false,"status":"invalid_image","message":"Image must be a valid base64 JPEG or PNG."}"""
        val faces = detectFaces(bitmap)
        val facesJson = JSONArray().apply {
            faces.forEach { face ->
                put(
                    JSONObject()
                        .put("x", face.centerX - face.width / 2f)
                        .put("y", face.centerY - face.height / 2f)
                        .put("width", face.width)
                        .put("height", face.height)
                        .put("confidence", face.confidence)
                )
            }
        }
        val root = JSONObject()
            .put("ok", true)
            .put("status", if (matchingRequested) "matching_not_configured" else "completed")
            .put("faces", facesJson)
            .put("count", faces.size)
        if (matchingRequested) {
            root.put("message", "Р’ СЌС‚РѕР№ СЃР±РѕСЂРєРµ live face matching С‚СЂРµР±СѓРµС‚ РѕС‚РґРµР»СЊРЅС‹Р№ embedder adapter. Р›РѕРєР°Р»СЊРЅР°СЏ РґРµС‚РµРєС†РёСЏ Р»РёС† СѓР¶Рµ СЂР°Р±РѕС‚Р°РµС‚.")
        }
        return root.toString()
    }

    private fun hubDownloadStatusJson(modelId: String): String {
        val installedModel = runBlocking { AppContainer.getModelRepository().getModelById(modelId) }
        val selected = resolvePreferredModelId("chat") == modelId
        val installedBytes = installedModel?.let { model ->
            when {
                model.pathType == PathType.DIRECTORY -> File(model.modelPath).takeIf { it.exists() }?.walkTopDown()?.sumOf { file ->
                    if (file.isFile) file.length() else 0L
                } ?: 0L
                else -> File(model.modelPath).takeIf { it.exists() }?.length() ?: 0L
            }
        } ?: 0L
        val response = mapDownloadStatusResponse(
            state = ModelDownloadService.downloadStates.value[modelId],
            installed = installedModel != null,
            selected = selected,
            installedBytes = installedBytes,
        )
        return serviceJson.encodeToString(response)
    }

    private fun collectGenerationFlow(
        flow: kotlinx.coroutines.flow.Flow<GenerationEvent>,
        callback: IGgufGenerationCallback
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                flow.collect { event ->
                    try {
                        when (event) {
                            is GenerationEvent.Token -> callback.onToken(event.text)
                            is GenerationEvent.Done -> callback.onDone()
                            is GenerationEvent.Error -> callback.onError(event.message)
                            is GenerationEvent.Metrics -> {
                                val m = event.metrics
                                callback.onMetrics(
                                    m.tokensPerSecond, m.timeToFirstTokenMs, m.totalTimeMs,
                                    m.tokensEvaluated, m.tokensPredicted,
                                    m.modelSizeMB, m.contextSizeMB, m.peakMemoryMB, m.memoryUsagePercent
                                )
                            }
                            is GenerationEvent.ToolCall -> callback.onToolCall(event.name, event.args)
                            is GenerationEvent.Progress -> callback.onProgress(event.progress)
                        }
                    } catch (e: DeadObjectException) {
                        Log.w(TAG, "Client disconnected during generation")
                        ggufEngine.stopGeneration()
                        return@collect
                    }
                }
            } catch (e: DeadObjectException) {
                Log.w(TAG, "Client disconnected during generation", e)
                ggufEngine.stopGeneration()
            } catch (e: Exception) {
                try {
                    callback.onError(e.message ?: "Unknown error")
                } catch (_: Exception) { }
            }
        }
    }

    private val binder = object : ILLMService.Stub() {

        override fun getRuntimeCapabilitiesJson(): String =
            gateExternalJson { modelRegistry.statusJson() }

        override fun listModelsJson(): String =
            gateExternalJson { externalCatalogJson(locale = null) }

        override fun listModelsJsonForLocale(locale: String?): String =
            gateExternalJson { externalCatalogJson(locale) }

        override fun searchCatalogJson(requestJson: String): String {
            val request = runCatching {
                serviceJson.decodeFromString<CatalogSearchRequest>(requestJson)
            }.getOrElse {
                return serviceJson.encodeToString(
                    ExternalDownloadResponse(
                        ok = false,
                        status = "invalid_request",
                        message = "Invalid catalog search request JSON.",
                    )
                )
            }
            return gateAuthenticatedJson(request.auth) {
                val items = searchCatalog(request).map { it.toExternalCatalogDescriptor(locale = null) }
                JSONObject()
                    .put("ok", true)
                    .put("count", items.size)
                    .put("items", JSONArray(serviceJson.encodeToString(items)))
                    .toString()
            }
        }

        override fun registerClientJson(requestJson: String): String {
            val request = runCatching {
                serviceJson.decodeFromString<ExternalClientRegistrationRequest>(requestJson)
            }.getOrElse {
                return serviceJson.encodeToString(
                    ExternalClientRegistrationResponse(
                        ok = false,
                        packageName = "",
                        message = "Invalid client registration JSON.",
                    )
                )
            }
            return serviceJson.encodeToString(runBlocking { externalAccessManager.registerClient(request) })
        }

        override fun getModelManifestSchemaJson(): String =
            gateExternalJson { modelRegistry.manifestSchemaJson() }

        override fun importModelManifestJson(manifestJson: String): String =
            gateExternalJson {
                modelRegistry.importManifest(manifestJson).fold(
                    onSuccess = { """{"ok":true,"id":"${it.id}","name":"${it.name}","runtime":"${it.runtime}"}""" },
                    onFailure = { """{"ok":false,"error":"${it.message ?: "Invalid model manifest"}"}""" }
                )
            }

        override fun downloadModelJson(requestJson: String): String =
            run {
                val request = runCatching {
                    serviceJson.decodeFromString<ExternalDownloadRequest>(requestJson)
                }.getOrElse {
                    return@run serviceJson.encodeToString(
                        ExternalDownloadResponse(
                            ok = false,
                            status = "invalid_request",
                            message = "Invalid download request JSON.",
                        ),
                    )
                }
                gateAuthenticatedJson(request.auth) {
                    val model = catalogModelById(request.modelId)
                        ?: return@gateAuthenticatedJson serviceJson.encodeToString(
                            ExternalDownloadResponse(
                                ok = false,
                                status = "model_not_found",
                                modelId = request.modelId.ifBlank { null },
                                message = "Requested model is not present in the AI Hub catalog.",
                            ),
                        )
                    startHubModelDownload(model)
                    serviceJson.encodeToString(
                        ExternalDownloadResponse(
                            ok = true,
                            status = "started",
                            modelId = model.id,
                            message = if (model.rawAssetOnly) {
                                "AI Hub started raw asset download. Local execution depends on runtime support."
                            } else {
                                "AI Hub started the model download."
                            },
                        ),
                    )
                }
            }

        override fun cancelDownloadJson(modelId: String): String =
            gateExternalJson {
                if (modelId.isBlank()) {
                    return@gateExternalJson serviceJson.encodeToString(
                        ExternalDownloadResponse(
                            ok = false,
                            status = "invalid_request",
                            message = "Model id is required to cancel a download.",
                        ),
                    )
                }
                val intent = Intent(applicationContext, ModelDownloadService::class.java).apply {
                    action = ModelDownloadService.ACTION_CANCEL_DOWNLOAD
                    putExtra(ModelDownloadService.EXTRA_MODEL_ID, modelId)
                }
                applicationContext.startService(intent)
                serviceJson.encodeToString(
                    ExternalDownloadResponse(
                        ok = true,
                        status = "canceled",
                        modelId = modelId,
                        message = "AI Hub cancel request sent.",
                    ),
                )
            }

        override fun getDownloadStatusJson(modelId: String): String =
            gateExternalJson { hubDownloadStatusJson(modelId) }

        override fun preparePreferredModelJson(capability: String): String =
            gateExternalJson {
                if (capability != "chat") {
                    return@gateExternalJson serviceJson.encodeToString(
                        ExternalPreparePreferredResponse(
                            ok = false,
                            runtime = null,
                            message = "Only chat preparation is implemented for external clients.",
                        ),
                    )
                }
                serviceJson.encodeToString(preparePreferredChatModel())
            }

        override fun runVisionJson(requestJson: String): String =
            run {
                if (requestJson.contains("\"imageRef\"", ignoreCase = true)) {
                    val request = runCatching {
                        serviceJson.decodeFromString<FaceRecognitionRequest>(requestJson)
                    }.getOrElse {
                        return@run """{"ok":false,"status":"invalid_request","message":"Invalid face recognition request JSON."}"""
                    }
                    return@run gateAuthenticatedJson(request.auth) {
                        detectFacesJson(request.imageRef, matchingRequested = true)
                    }
                }
                val request = runCatching {
                    serviceJson.decodeFromString<HubExecutionRequest>(requestJson)
                }.getOrElse {
                    return@run """{"ok":false,"status":"invalid_request","message":"Invalid vision request JSON."}"""
                }
                gateAuthenticatedJson(request.auth) {
                    val inputJson = runCatching { JSONObject(request.inputJson.ifBlank { "{}" }) }.getOrDefault(JSONObject())
                    val imageBase64 = extractImageBase64(inputJson)
                        ?: return@gateAuthenticatedJson """{"ok":false,"status":"invalid_request","message":"inputJson must include imageBase64."}"""
                    when (request.capability) {
                        "face_detection" -> detectFacesJson(imageBase64, matchingRequested = false)
                        "face_recognition" -> detectFacesJson(imageBase64, matchingRequested = true)
                        "object_detection",
                        "assistant_live",
                        "image_segmentation",
                        "text",
                        "ocr" -> executeVisionPrompt(buildVisionPrompt(request.capability, inputJson), imageBase64)

                        else -> """{"ok":false,"status":"unsupported_capability","message":"Vision capability ${request.capability} is not available in this build."}"""
                    }
                }
            }

        override fun runImageJson(requestJson: String): String =
            run {
                val request = runCatching {
                    serviceJson.decodeFromString<HubExecutionRequest>(requestJson)
                }.getOrElse {
                    val videoRequest = runCatching {
                        serviceJson.decodeFromString<VideoGenerationRequest>(requestJson)
                    }.getOrElse {
                        return@run """{"ok":false,"status":"invalid_request","message":"Invalid image request JSON."}"""
                    }
                    return@run gateAuthenticatedJson(videoRequest.auth) {
                        JSONObject()
                            .put("ok", false)
                            .put("status", "catalog_only")
                            .put("mode", videoRequest.mode)
                            .put("message", "Video generation is catalog-first in this build. Use LAN worker or a future local video runtime.")
                            .toString()
                    }
                }
                gateAuthenticatedJson(request.auth) {
                    if (request.capability == "video_generation") {
                        return@gateAuthenticatedJson JSONObject()
                            .put("ok", false)
                            .put("status", "catalog_only")
                            .put("mode", request.orchestrationMode)
                            .put("message", "Video generation is catalog-first in this build. Use LAN worker or a future local video runtime.")
                            .toString()
                    }
                    JSONObject()
                        .put("ok", false)
                        .put("status", "use_diffusion_aidl")
                        .put("message", "Use loadDiffusionModel and generateDiffusionImage for streaming image generation in this build.")
                        .toString()
                }
            }

        override fun getHttpApiStateJson(): String =
            gateExternalJson { httpApiController.stateJson() }

        override fun runWithModeJson(requestJson: String): String =
            run {
                val request = runCatching {
                    serviceJson.decodeFromString<HubExecutionRequest>(requestJson)
                }.getOrElse {
                    return@run """{"ok":false,"status":"invalid_request","message":"Invalid execution request JSON."}"""
                }
                gateAuthenticatedJson(request.auth) {
                    val config = runBlocking { appSettings.orchestraConfigSnapshot() }
                    val capabilityState = orchestraManager.capabilityState(installedModelsSnapshot(), config)
                    val requestedMode = request.orchestrationMode.lowercase()
                    val useOrchestra = requestedMode == "small_model_orchestra" && config.enabled && capabilityState.supported
                    val appliedMode = if (useOrchestra) "small_model_orchestra" else "single_model"
                    val baseResponse = when (request.capability) {
                        "chat", "files", "summary", "code", "instructions", "live_reasoning" -> executeChatRequest(request)
                        else -> JSONObject()
                            .put("ok", false)
                            .put("status", "unsupported_capability")
                            .put("message", "Capability ${request.capability} is not implemented in runWithModeJson.")
                            .toString()
                    }
                    val response = runCatching { JSONObject(baseResponse) }.getOrElse {
                        return@gateAuthenticatedJson baseResponse
                    }
                    response.put("requestedMode", requestedMode)
                    response.put("appliedMode", appliedMode)
                    response.put("orchestraSupported", capabilityState.supported)
                    if (!useOrchestra && requestedMode == "small_model_orchestra") {
                        response.put("fallbackReason", capabilityState.reason)
                        if (config.allowLanSpillover) {
                            response.put("lanFallbackEligible", true)
                        }
                    }
                    response.toString()
                }
            }

        override fun getPreferredModelsJson(): String =
            gateExternalJson {
                serviceJson.encodeToString(runBlocking { appSettings.preferredModelsSnapshot() })
            }

        override fun setPreferredModelJson(capability: String, modelId: String?): String =
            gateExternalJson {
                val current = runBlocking { appSettings.preferredModelsSnapshot() }
                val normalizedModelId = modelId?.takeIf { it.isNotBlank() }
                val updated = when (capability) {
                    "chat" -> current.copy(chatModelId = normalizedModelId)
                    "vision" -> current.copy(visionModelId = normalizedModelId)
                    "image_generation" -> current.copy(imageGenerationModelId = normalizedModelId)
                    "video_generation" -> current.copy(videoGenerationModelId = normalizedModelId)
                    "tts" -> current.copy(ttsModelId = normalizedModelId)
                    "files" -> current.copy(filesModelId = normalizedModelId)
                    "assistant_live" -> current.copy(assistantLiveModelId = normalizedModelId)
                    else -> current
                }
                runBlocking { appSettings.savePreferredModels(updated) }
                serviceJson.encodeToString(updated)
            }

        override fun getExternalAccessPolicyJson(): String =
            serviceJson.encodeToString(externalAccessManager.policySnapshot())

        override fun approveClientJson(packageName: String): String {
            runBlocking { externalAccessManager.approve(packageName) }
            return serviceJson.encodeToString(externalAccessManager.policySnapshot())
        }

        override fun revokeClientJson(packageName: String): String {
            runBlocking { externalAccessManager.revoke(packageName) }
            return serviceJson.encodeToString(externalAccessManager.policySnapshot())
        }

        override fun listLanNodesJson(): String =
            gateExternalJson {
                val config = runBlocking { appSettings.lanHubConfigSnapshot() }
                lanCoordinator.nodesJson(installedModelsSnapshot(), lanCoordinator.ensurePairingToken(config))
            }

        override fun getDistributedGgufPlanJson(modelId: String): String =
            gateExternalJson {
                val config = runBlocking { appSettings.lanHubConfigSnapshot() }
                lanCoordinator.distributedPlanJson(
                    models = installedModelsSnapshot(),
                    modelId = modelId,
                    config = lanCoordinator.ensurePairingToken(config)
                )
            }

        override fun getOrchestraConfigJson(): String =
            gateExternalJson {
                serviceJson.encodeToString(runBlocking { appSettings.orchestraConfigSnapshot() })
            }

        override fun setOrchestraConfigJson(configJson: String): String =
            gateExternalJson {
                val updated = runCatching {
                    serviceJson.decodeFromString<OrchestraConfig>(configJson)
                }.getOrElse { OrchestraConfig() }
                runBlocking { appSettings.saveOrchestraConfig(updated) }
                serviceJson.encodeToString(updated)
            }

        // РІвЂќР‚РІвЂќР‚ GGUF Methods РІвЂќР‚РІвЂќР‚

        override fun loadGgufModel(
            modelPath: String,
            modelName: String,
            loadingParams: String,
            inferenceParams: String,
            callback: IModelLoadCallback
        ) {
            scope.launch(Dispatchers.IO) {
                try {
                    AppStateManager.setLoadingModel(modelName)

                    val model = Model(
                        id = modelName,
                        modelPath = modelPath,
                        modelName = modelName,
                        pathType = PathType.FILE,
                        providerType = ProviderType.GGUF,
                        fileSize = null
                    )
                    val config = ModelConfig(
                        modelId = modelName,
                        modelLoadingParams = loadingParams,
                        modelInferenceParams = inferenceParams
                    )

                    val success = ggufEngine.load(model, config)

                    if (success) {
                        AppStateManager.setModelLoaded(modelName)
                        callback.onSuccess()
                    } else {
                        AppStateManager.setError("Failed to load model: $modelName")
                        callback.onError("Failed to load model")
                    }
                } catch (e: Exception) {
                    AppStateManager.setError(e.message ?: "Unknown error loading model")
                    callback.onError(e.message ?: "Unknown error")
                }
            }
        }

        override fun loadGgufModelFromFd(
            pfd: ParcelFileDescriptor,
            modelName: String,
            loadingParams: String,
            inferenceParams: String,
            callback: IModelLoadCallback
        ) {
            // Detach fd synchronously before coroutine launch РІР‚вЂќ
            // Binder may close the ParcelFileDescriptor after the AIDL call returns
            val fd = pfd.detachFd()

            scope.launch(Dispatchers.IO) {
                try {
                    AppStateManager.setLoadingModel(modelName)

                    val config = ModelConfig(
                        modelId = modelName,
                        modelLoadingParams = loadingParams,
                        modelInferenceParams = inferenceParams
                    )

                    val success = ggufEngine.loadFromFd(fd, config)

                    if (success) {
                        AppStateManager.setModelLoaded(modelName)
                        callback.onSuccess()
                    } else {
                        AppStateManager.setError("Failed to load model from FD: $modelName")
                        callback.onError("Failed to load model from file descriptor")
                    }
                } catch (e: Exception) {
                    AppStateManager.setError(e.message ?: "Unknown error loading model from FD")
                    callback.onError(e.message ?: "Unknown error")
                }
            }
        }

        override fun generateGguf(
            prompt: String, maxTokens: Int, callback: IGgufGenerationCallback
        ) {
            collectGenerationFlow(ggufEngine.generateFlow(prompt, maxTokens), callback)
        }

        override fun stopGenerationGguf() {
            ggufEngine.stopGeneration()
        }

        override fun unloadModelGguf() {
            scope.launch(Dispatchers.IO) {
                ggufEngine.unload()
                AppStateManager.setModelUnloaded()
            }
        }

        override fun getModelInfoGguf(): String? = ggufEngine.getModelInfo()

        override fun setToolsJsonGguf(toolsJson: String): Boolean =
            ggufEngine.setToolsJson(toolsJson)

        override fun clearToolsGguf() {
            ggufEngine.clearTools()
        }

        // РІвЂќР‚РІвЂќР‚ Multi-turn Tool Calling РІвЂќР‚РІвЂќР‚

        override fun enableToolCallingGguf(
            toolsJson: String, grammarMode: Int, useTypedGrammar: Boolean
        ): Boolean = ggufEngine.enableToolCalling(toolsJson, grammarMode, useTypedGrammar)

        override fun generateGgufMultiTurn(
            messagesJson: String, maxTokens: Int, callback: IGgufGenerationCallback
        ) {
            collectGenerationFlow(ggufEngine.generateMultiTurnFlow(messagesJson, maxTokens), callback)
        }

        override fun setGrammarModeGguf(mode: Int) {
            // Grammar mode applied via tool calling config
        }

        override fun setTypedGrammarGguf(enabled: Boolean) {
            // Grammar mode applied via tool calling config
        }

        override fun isToolCallingSupportedGguf(): Boolean =
            ggufEngine.isToolCallingSupported()

        // РІвЂќР‚РІвЂќР‚ Persona Engine РІвЂќР‚РІвЂќР‚

        override fun updateSamplerParamsGguf(paramsJson: String): Boolean =
            ggufEngine.updateSamplerParams(paramsJson)

        override fun setLogitBiasGguf(biasJson: String): Boolean =
            ggufEngine.setLogitBias(biasJson)

        override fun loadControlVectorsGguf(vectorsJson: String): Boolean =
            ggufEngine.loadControlVectors(vectorsJson)

        override fun clearControlVectorGguf(): Boolean =
            ggufEngine.clearControlVector()

        // РІвЂќР‚РІвЂќР‚ KV Cache State Persistence РІвЂќР‚РІвЂќР‚

        override fun getStateSizeGguf(): Long = ggufEngine.getStateSize()
        override fun stateSaveToFileGguf(path: String): Boolean = ggufEngine.stateSaveToFile(path)
        override fun stateLoadFromFileGguf(path: String): Boolean = ggufEngine.stateLoadFromFile(path)

        // РІвЂќР‚РІвЂќР‚ New Optimizations РІвЂќР‚РІвЂќР‚

        override fun setSpeculativeDecodingGguf(enabled: Boolean, nDraft: Int, ngramSize: Int) {
            ggufEngine.setSpeculativeDecoding(enabled, nDraft, ngramSize)
        }

        override fun setPromptCacheDirGguf(path: String) {
            ggufEngine.setPromptCacheDir(path)
        }

        override fun warmUpGguf(): Boolean = ggufEngine.warmUp()

        override fun supportsThinkingGguf(): Boolean = ggufEngine.supportsThinking()

        override fun setThinkingEnabledGguf(enabled: Boolean) {
            ggufEngine.setThinkingEnabled(enabled)
        }

        override fun getContextUsageGguf(): Float = ggufEngine.getContextUsage()

        // РІвЂќР‚РІвЂќР‚ Context Window Tracking РІвЂќР‚РІвЂќР‚

        override fun getContextInfoGguf(prompt: String?): String {
            val info = ggufEngine.getContextInfo(prompt)
            return org.json.JSONObject().apply {
                put("total", info.total)
                put("used", info.used)
                put("remaining", info.remaining)
                put("promptEstimate", info.promptEstimate)
                put("afterPrompt", info.afterPrompt)
            }.toString()
        }

        // РІвЂќР‚РІвЂќР‚ Character Engine РІвЂќР‚РІвЂќР‚

        override fun setPersonalityGguf(personalityJson: String): Boolean =
            ggufEngine.setPersonality(personalityJson)

        override fun setMoodGguf(mood: Int): Boolean =
            ggufEngine.setMood(mood)

        override fun setCustomMoodGguf(tempMod: Float, topPMod: Float, repPenaltyMod: Float): Boolean =
            ggufEngine.setCustomMood(tempMod, topPMod, repPenaltyMod)

        override fun getCharacterContextGguf(): String =
            ggufEngine.getCharacterContext()

        override fun buildPromptGguf(userPrompt: String): String =
            ggufEngine.buildPrompt(userPrompt)

        override fun setUncensoredGguf(enabled: Boolean): Boolean =
            ggufEngine.setUncensored(enabled)

        override fun isUncensoredGguf(): Boolean =
            ggufEngine.isUncensored()

        // РІвЂќР‚РІвЂќР‚ Upscaler РІвЂќР‚РІвЂќР‚

        override fun loadUpscaler(modelPath: String, callback: IModelLoadCallback) {
            scope.launch(Dispatchers.IO) {
                try {
                    val success = diffusionEngine.loadUpscaler(modelPath)
                    if (success) callback.onSuccess()
                    else callback.onError("Failed to load upscaler")
                } catch (e: Exception) {
                    callback.onError(e.message ?: "Unknown error loading upscaler")
                }
            }
        }

        override fun releaseUpscaler() {
            diffusionEngine.releaseUpscaler()
        }

        // РІвЂќР‚РІвЂќР‚ Diffusion Methods РІвЂќР‚РІвЂќР‚

        override fun loadDiffusionModel(
            name: String,
            modelDir: String,
            height: Int,
            width: Int,
            textEmbeddingSize: Int,
            runOnCpu: Boolean,
            useCpuClip: Boolean,
            isPony: Boolean,
            httpPort: Int,
            safetyMode: Boolean,
            callback: IModelLoadCallback
        ) {
            scope.launch(Dispatchers.IO) {
                try {
                    Log.i(TAG, "Loading diffusion model: $name")
                    AppStateManager.setLoadingModel(name)

                    val result = diffusionEngine.loadModel(
                        name = name,
                        modelDir = modelDir,
                        textEmbeddingSize = textEmbeddingSize,
                        runOnCpu = runOnCpu,
                        useCpuClip = useCpuClip,
                        isPony = isPony,
                        httpPort = httpPort,
                        safetyMode = safetyMode,
                        height = height,
                        width = width
                    )

                    result.fold(onSuccess = { message ->
                        AppStateManager.setModelLoaded(name)
                        callback.onSuccess()
                        Log.i(TAG, "Diffusion model loaded: $message")
                    }, onFailure = { error ->
                        val errorMsg = error.message ?: "Failed to load diffusion model"
                        AppStateManager.setError(errorMsg)
                        callback.onError(errorMsg)
                        Log.e(TAG, "Failed to load diffusion model", error)
                    })
                } catch (e: Exception) {
                    val errorMsg = e.message ?: "Unknown error loading diffusion model"
                    AppStateManager.setError(errorMsg)
                    callback.onError(errorMsg)
                    Log.e(TAG, "Exception loading diffusion model", e)
                }
            }
        }

        override fun generateDiffusionImage(
            prompt: String,
            negativePrompt: String,
            steps: Int,
            cfgScale: Float,
            seed: Long,
            width: Int,
            height: Int,
            scheduler: String,
            useOpenCL: Boolean,
            inputImage: String?,
            mask: String?,
            denoiseStrength: Float,
            showDiffusionProcess: Boolean,
            showDiffusionStride: Int,
            callback: IDiffusionGenerationCallback
        ) {
            scope.launch(Dispatchers.IO) {
                try {
                    Log.i(TAG, "Starting diffusion generation: $prompt")

                    diffusionEngine.generateImage(
                        prompt = prompt,
                        negativePrompt = negativePrompt,
                        steps = steps,
                        cfgScale = cfgScale,
                        seed = if (seed == -1L) null else seed,
                        width = width,
                        height = height,
                        scheduler = scheduler,
                        useOpenCL = useOpenCL,
                        inputImage = inputImage,
                        mask = mask,
                        denoiseStrength = denoiseStrength,
                        showDiffusionProcess = showDiffusionProcess,
                        showDiffusionStride = showDiffusionStride
                    )

                    diffusionEngine.observeGenerationState(onProgress = { progress, currentStep, totalSteps, intermediateBitmap ->
                        try {
                            val imageBase64 = intermediateBitmap?.let {
                                diffusionEngine.bitmapToBase64(it, quality = 80)
                            } ?: ""

                            callback.onProgress(progress, currentStep, totalSteps, imageBase64)
                            Log.d(TAG, "Generation progress: ${(progress * 100).toInt()}%")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending progress", e)
                        }
                    }, onComplete = { bitmap, completedSeed, resultWidth, resultHeight ->
                        try {
                            val imageBase64 = diffusionEngine.bitmapToBase64(bitmap)
                            callback.onComplete(imageBase64, completedSeed ?: -1L, resultWidth, resultHeight)
                            Log.i(TAG, "Generation completed successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending completion", e)
                            callback.onError(e.message ?: "Error processing result")
                        }
                    }, onError = { errorMessage ->
                        try {
                            callback.onError(errorMessage)
                            Log.e(TAG, "Generation error: $errorMessage")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending error callback", e)
                        }
                    })
                } catch (e: Exception) {
                    try {
                        callback.onError(e.message ?: "Unknown error during generation")
                        Log.e(TAG, "Exception in generateDiffusionImage", e)
                    } catch (_: Exception) { }
                }
            }
        }

        override fun stopGenerationDiffusion() {
            diffusionEngine.cancelGeneration()
            Log.i(TAG, "Diffusion generation stopped")
        }

        override fun restartDiffusionBackend(callback: IModelLoadCallback) {
            scope.launch(Dispatchers.IO) {
                try {
                    val success = diffusionEngine.restartBackend()
                    if (success) callback.onSuccess()
                    else callback.onError("Failed to restart backend")
                } catch (e: Exception) {
                    callback.onError(e.message ?: "Unknown error restarting backend")
                }
            }
        }

        override fun stopDiffusionBackend() {
            diffusionEngine.stopBackend()
            Log.i(TAG, "Diffusion backend stopped")
        }

        override fun getDiffusionBackendState(): String =
            diffusionEngine.getBackendStateString()

        override fun getCurrentDiffusionModel(): String? {
            val model = diffusionEngine.getCurrentModel()
            return model?.let { "${it.name})" }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_WAKE_HUB -> {
                Log.i(TAG, "Hub wake request received")
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)

        // Tell llama.cpp where to find CPU backend variant .so files
        // (libggml-cpu-android_armv8.*.so) for runtime arch-level dispatch.
        try {
            val engineClass = Class.forName("com.dark.gguf_lib.GGMLEngine")
            val initMethod = engineClass.getMethod("initBackendDir", android.content.Context::class.java)
            initMethod.invoke(null, applicationContext)
        } catch (_: Throwable) {
            // Old AAR without initBackendDir РІР‚вЂќ dladdr() fallback handles it
        }

        scope.launch(Dispatchers.IO) {
            try {
                diffusionEngine.init(applicationContext, safetyCheckerEnabled = true)
                Log.i(TAG, "DiffusionEngine initialized in LLMService")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize diffusion engine", e)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, 1, createNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                else 0
            )
        } else {
            startForeground(1, createNotification())
        }
    }

    override fun onDestroy() {
        instance = null
        runBlocking(Dispatchers.IO) {
            runCatching { ggufEngine.unload() }
                .onFailure { Log.w(TAG, "Failed to unload GGUF engine during service shutdown", it) }
            runCatching { diffusionEngine.cleanup() }
                .onFailure { Log.w(TAG, "Failed to cleanup diffusion engine during service shutdown", it) }
        }
        scope.cancel()
        super.onDestroy()
        Log.i(TAG, "LLMService destroyed")
    }

    private fun createNotification(): Notification {
        val channelId = "llm_service"
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId, "LLM Service", NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId).setContentTitle("AI Model Service")
            .setContentText("Running...").setSmallIcon(R.drawable.user)
            .setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true).setSilent(true).build()
    }
}



