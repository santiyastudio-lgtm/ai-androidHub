package com.santiya.localaihub.catalog

import android.util.Log
import com.santiya.localaihub.hub.CatalogWarning
import com.santiya.localaihub.hub.Downloadability
import com.santiya.localaihub.hub.ModelSupportStatus
import com.santiya.localaihub.models.data.HFModelRepository
import com.santiya.localaihub.models.data.HuggingFaceModel
import com.santiya.localaihub.models.data.ModelType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class UniversalCatalogRepository {
    private val client = OkHttpClient.Builder().build()
    private val adapters: List<CatalogSourceAdapter> = listOf(
        HuggingFaceCatalogAdapter(client),
        CivitaiCatalogAdapter(client),
        ModelScopeCatalogAdapter(client),
        GitHubCatalogAdapter(client),
    )

    suspend fun search(
        query: String,
        filters: CatalogSearchFilters,
        curatedModels: List<HuggingFaceModel>,
        repositories: List<HFModelRepository>,
        limit: Int = 36,
    ): List<HuggingFaceModel> {
        val normalizedQuery = query.trim()
        val starter = starterModels()
        val local = (curatedModels + starter)
            .map { enrichLocalModel(it, repositories) }
            .filter { matchesQuery(it, normalizedQuery) }
            .filter { matchesFilters(it, filters) }

        if (normalizedQuery.isBlank()) {
            return (local + starter)
                .distinctBy { it.id }
                .sortedWith(compareBy<HuggingFaceModel>({ sourcePriority(it.source) }, { it.name.lowercase(Locale.US) }))
        }

        val remote = adapters
            .filter { it.source in filters.sources }
            .flatMap { adapter ->
                runCatching { adapter.search(normalizedQuery, filters, limit) }
                    .onFailure { Log.w(TAG, "Catalog source ${adapter.source.wireName} failed: ${it.message}") }
                    .getOrDefault(emptyList())
            }
            .map { enrichLocalModel(it, repositories) }
            .filter { matchesFilters(it, filters) }

        return (local + remote + starter.filter { matchesQuery(it, normalizedQuery) })
            .distinctBy { it.id }
            .sortedWith(compareBy<HuggingFaceModel>({ sourcePriority(it.source) }, { it.name.lowercase(Locale.US) }))
            .take(limit * 2)
    }

    fun starterModels(): List<HuggingFaceModel> = listOf(
        HuggingFaceModel(
            id = "onnx/yolov8n",
            name = "YOLOv8n ONNX",
            description = "Mobile-friendly object detection model.",
            fileUri = "onnx-community/yolov8n/resolve/main/onnx/model.onnx",
            approximateSize = "13 MB",
            modelType = ModelType.GGUF,
            isZip = false,
            runOnCpu = true,
            tags = listOf("vision", "object_detection", "onnx", "CPU"),
            displayNameRu = "YOLOv8n ONNX",
            descriptionRu = "Лёгкая ONNX-модель для распознавания объектов на устройстве.",
            supportStatus = ModelSupportStatus.LOCAL,
            downloadability = Downloadability.LOCAL_RUNNABLE,
            source = CatalogSource.HUGGING_FACE.wireName,
            sourceLabel = CatalogSource.HUGGING_FACE.displayName,
            capabilities = listOf("object_detection", "assistant_live"),
            familyTags = listOf("YOLO"),
            warnings = emptyList(),
        ),
        HuggingFaceModel(
            id = "onnx/ultraface",
            name = "UltraFace",
            description = "Fast face detection model.",
            fileUri = "onnx-community/ultraface/resolve/main/model.onnx",
            approximateSize = "1 MB",
            modelType = ModelType.GGUF,
            isZip = false,
            runOnCpu = true,
            tags = listOf("vision", "face_detection", "onnx", "CPU"),
            displayNameRu = "UltraFace",
            descriptionRu = "Быстрая модель для детекции лиц в потоке с камеры.",
            supportStatus = ModelSupportStatus.LOCAL,
            downloadability = Downloadability.LOCAL_RUNNABLE,
            source = CatalogSource.HUGGING_FACE.wireName,
            sourceLabel = CatalogSource.HUGGING_FACE.displayName,
            capabilities = listOf("face_detection", "assistant_live"),
            familyTags = listOf("Face"),
            warnings = emptyList(),
        ),
        HuggingFaceModel(
            id = "onnx/mobilefacenet",
            name = "MobileFaceNet",
            description = "Face embedding model for local identity matching.",
            fileUri = "ModelScope/ArcFace/resolve/main/mobilefacenet.onnx",
            approximateSize = "5 MB",
            modelType = ModelType.GGUF,
            isZip = false,
            runOnCpu = true,
            tags = listOf("vision", "face_recognition", "onnx", "CPU"),
            displayNameRu = "MobileFaceNet",
            descriptionRu = "Модель эмбеддингов лиц для сценариев Santiya Security и внешних приложений.",
            supportStatus = ModelSupportStatus.EXPERIMENTAL,
            source = CatalogSource.MODELSCOPE.wireName,
            sourceLabel = CatalogSource.MODELSCOPE.displayName,
            pageUrl = "https://www.modelscope.cn/models/ModelScope/ArcFace",
            capabilities = listOf("face_recognition"),
            familyTags = listOf("Face"),
            rawAssetOnly = true,
            downloadability = Downloadability.RAW_ASSET_DOWNLOAD,
            warnings = experimentalWarnings("Модель можно скачать, но для локального запуска нужен совместимый ONNX adapter."),
        ),
        HuggingFaceModel(
            id = "video/wan2.1",
            name = "Wan 2.1",
            description = "Open video generation family.",
            fileUri = "",
            approximateSize = "Raw asset",
            modelType = ModelType.SD,
            isZip = false,
            runOnCpu = false,
            tags = listOf("video", "video_generation"),
            displayNameRu = "Wan 2.1",
            descriptionRu = "Серия открытых моделей генерации видео. В этой сборке доступна как каталог и raw asset.",
            supportStatus = ModelSupportStatus.EXPERIMENTAL,
            source = CatalogSource.HUGGING_FACE.wireName,
            sourceLabel = CatalogSource.HUGGING_FACE.displayName,
            pageUrl = "https://huggingface.co/models?search=Wan%202.1",
            capabilities = listOf("video_generation"),
            familyTags = listOf("Video"),
            rawAssetOnly = true,
            experimental = true,
            downloadability = Downloadability.RAW_ASSET_DOWNLOAD,
            warnings = experimentalWarnings("Видео-модель будет сохранена как raw asset. Высокий шанс, что она не запустится на этом устройстве."),
        ),
        HuggingFaceModel(
            id = "video/cogvideox",
            name = "CogVideoX",
            description = "Open video generation family.",
            fileUri = "",
            approximateSize = "Raw asset",
            modelType = ModelType.SD,
            isZip = false,
            runOnCpu = false,
            tags = listOf("video", "video_generation"),
            displayNameRu = "CogVideoX",
            descriptionRu = "Серия открытых моделей генерации видео с честной пометкой experimental.",
            supportStatus = ModelSupportStatus.EXPERIMENTAL,
            source = CatalogSource.HUGGING_FACE.wireName,
            sourceLabel = CatalogSource.HUGGING_FACE.displayName,
            pageUrl = "https://huggingface.co/models?search=CogVideoX",
            capabilities = listOf("video_generation"),
            familyTags = listOf("Video"),
            rawAssetOnly = true,
            experimental = true,
            downloadability = Downloadability.RAW_ASSET_DOWNLOAD,
            warnings = experimentalWarnings("Видео-модель будет сохранена как raw asset. Для запуска лучше использовать LAN-узел."),
        ),
        HuggingFaceModel(
            id = "tts/piper-ru",
            name = "Piper RU Irina",
            description = "Russian local TTS voice pack based on Piper.",
            fileUri = "Trelis/piper-ru-ru-irina-medium/resolve/main/model.onnx",
            approximateSize = "63 MB",
            modelType = ModelType.TTS,
            isZip = false,
            runOnCpu = true,
            tags = listOf("tts", "ru", "voice"),
            displayNameRu = "Piper RU Irina",
            descriptionRu = "Русский локальный голос для озвучки ответов и live-интерфейса.",
            supportStatus = ModelSupportStatus.EXPERIMENTAL,
            source = CatalogSource.HUGGING_FACE.wireName,
            sourceLabel = CatalogSource.HUGGING_FACE.displayName,
            pageUrl = "https://huggingface.co/Trelis/piper-ru-ru-irina-medium",
            downloadUrlOverride = "https://huggingface.co/Trelis/piper-ru-ru-irina-medium/resolve/main/model.onnx?download=true",
            resolvedFileName = "model.onnx",
            capabilities = listOf("tts"),
            familyTags = listOf("RU"),
            rawAssetOnly = false,
            experimental = true,
            downloadability = Downloadability.RAW_ASSET_DOWNLOAD,
            warnings = experimentalWarnings("Русский voice pack скачивается как Piper runtime. Проверьте, что на устройстве доступна ONNX/Piper-озвучка."),
        ),
    ) + gemmaAggressiveStarterModels()

    private fun enrichLocalModel(model: HuggingFaceModel, repositories: List<HFModelRepository>): HuggingFaceModel {
        if (model.source != CatalogSource.HUGGING_FACE.wireName) return model
        val repo = repositories.firstOrNull { repoDef ->
            model.id.startsWith(repoDef.id) || model.repositoryUrl.equals(repoDef.repoPath, ignoreCase = true)
        }
        val translatedFamilyTags = buildList {
            addAll(model.familyTags)
            if (repo?.category?.name?.equals("UNCENSORED", ignoreCase = true) == true) add("Uncensored")
            if (model.name.contains("Abliterated", ignoreCase = true)) add("Abliterated")
            if (model.name.contains("Dolphin", ignoreCase = true) || model.description.contains("Dolphin", ignoreCase = true)) add("Dolphin")
        }.distinct()
        val capabilityTags = if (model.capabilities.isNotEmpty()) model.capabilities else inferCapabilities(model.tags, model.name, model.description)
        return model.copy(
            source = model.source.ifBlank { CatalogSource.HUGGING_FACE.wireName },
            sourceLabel = model.sourceLabel.ifBlank { CatalogSource.HUGGING_FACE.displayName },
            pageUrl = model.pageUrl ?: model.repositoryUrl.takeIf { it.isNotBlank() }?.let { "https://huggingface.co/$it" },
            capabilities = capabilityTags,
            familyTags = translatedFamilyTags,
        )
    }

    private fun matchesQuery(model: HuggingFaceModel, query: String): Boolean {
        if (query.isBlank()) return true
        val haystack = buildString {
            append(model.name).append(' ')
            append(model.description).append(' ')
            append(model.displayNameRu.orEmpty()).append(' ')
            append(model.descriptionRu.orEmpty()).append(' ')
            append(model.tags.joinToString(" ")).append(' ')
            append(model.familyTags.joinToString(" ")).append(' ')
            append(model.capabilities.joinToString(" "))
        }
        return haystack.contains(query, ignoreCase = true)
    }

    private fun matchesFilters(model: HuggingFaceModel, filters: CatalogSearchFilters): Boolean {
        if (filters.sources.isNotEmpty() && CatalogSource.fromWireName(model.source)?.let { it !in filters.sources } == true) {
            return false
        }
        if (!filters.includeNsfw && model.tags.any { it.equals("NSFW", ignoreCase = true) }) return false
        if (filters.capabilities.isNotEmpty() && model.capabilities.none { capability ->
                filters.capabilities.any { selected -> selected.equals(capability, ignoreCase = true) }
            }) {
            return false
        }
        if (filters.familyTags.isNotEmpty() && model.familyTags.none { family ->
                filters.familyTags.any { selected -> selected.equals(family, ignoreCase = true) }
            } && model.name.let { name -> filters.familyTags.none { name.contains(it, ignoreCase = true) } }) {
            return false
        }
        if (filters.supportStatuses.isNotEmpty() && model.supportStatus?.let { it !in filters.supportStatuses } == true) {
            return false
        }
        if (filters.executionTargets.isNotEmpty() && model.tags.none { tag ->
                filters.executionTargets.any { selected -> selected.equals(tag, ignoreCase = true) }
            }) {
            return false
        }
        return true
    }

    private fun sourcePriority(source: String): Int = when (source.lowercase(Locale.US)) {
        CatalogSource.HUGGING_FACE.wireName -> 0
        CatalogSource.CIVITAI.wireName -> 1
        CatalogSource.MODELSCOPE.wireName -> 2
        CatalogSource.GITHUB.wireName -> 3
        else -> 4
    }

    companion object {
        private const val TAG = "UniversalCatalogRepo"

        internal fun inferCapabilities(tags: List<String>, name: String, description: String): List<String> {
            val text = buildString {
                append(name).append(' ')
                append(description).append(' ')
                append(tags.joinToString(" "))
            }
            val capabilities = linkedSetOf<String>()
            if (text.contains("gguf", ignoreCase = true) || text.contains("chat", ignoreCase = true)) capabilities += "chat"
            if (text.contains("object", ignoreCase = true) || text.contains("yolo", ignoreCase = true) || text.contains("ssd", ignoreCase = true)) capabilities += "object_detection"
            if (text.contains("face detection", ignoreCase = true) || text.contains("retinaface", ignoreCase = true) || text.contains("ultraface", ignoreCase = true) || text.contains("blazeface", ignoreCase = true)) capabilities += "face_detection"
            if (text.contains("face recognition", ignoreCase = true) || text.contains("arcface", ignoreCase = true) || text.contains("mobilefacenet", ignoreCase = true)) capabilities += "face_recognition"
            if (text.contains("segmentation", ignoreCase = true) || text.contains("sam", ignoreCase = true)) capabilities += "image_segmentation"
            if (text.contains("diffusion", ignoreCase = true) || text.contains("sdxl", ignoreCase = true) || text.contains("text-to-image", ignoreCase = true) || text.contains("image generation", ignoreCase = true)) capabilities += "image_generation"
            if (text.contains("video", ignoreCase = true)) capabilities += "video_generation"
            if (text.contains("tts", ignoreCase = true) || text.contains("voice", ignoreCase = true) || text.contains("speech", ignoreCase = true)) capabilities += "tts"
            if (capabilities.isEmpty()) capabilities += "chat"
            return capabilities.toList()
        }
    }
}

private fun gemmaAggressiveStarterModels(): List<HuggingFaceModel> {
    val repoPath = "HauhauCS/Gemma-4-E2B-Uncensored-HauhauCS-Aggressive"
    val pageUrl = "https://huggingface.co/$repoPath"
    val commonFamilyTags = listOf("Gemma", "Uncensored")
    val commonWarnings = experimentalWarnings(
        "GGUF-модель можно скачать и попробовать запустить локально, но для этой серии нужен запас RAM. Для картинок и live отдельно докачайте mmproj."
    )

    fun gemmaModel(
        idSuffix: String,
        fileName: String,
        sizeLabel: String,
        ramEstimateMb: Int,
        quantTag: String
    ) = HuggingFaceModel(
        id = "gemma4-e2b-$idSuffix",
        name = fileName.removeSuffix(".gguf"),
        description = "Gemma 4 E2B Uncensored Aggressive GGUF build.",
        fileUri = "$repoPath/resolve/main/$fileName",
        approximateSize = sizeLabel,
        modelType = ModelType.GGUF,
        isZip = false,
        runOnCpu = true,
        tags = listOf("GGUF", quantTag, "CPU", "Experimental"),
        displayNameRu = fileName.removeSuffix(".gguf"),
        descriptionRu = "Uncensored-версия Gemma 4 E2B в квантизации $quantTag. Подходит для локального чата, но требует заметный запас памяти.",
        ramEstimateMb = ramEstimateMb,
        supportStatus = ModelSupportStatus.EXPERIMENTAL,
        assistantEligible = true,
        liveEligible = false,
        experimental = true,
        source = CatalogSource.HUGGING_FACE.wireName,
        sourceLabel = CatalogSource.HUGGING_FACE.displayName,
        pageUrl = pageUrl,
        resolvedFileName = fileName,
        capabilities = listOf("chat"),
        familyTags = commonFamilyTags,
        rawAssetOnly = false,
        downloadability = Downloadability.LOCAL_RUNNABLE,
        warnings = commonWarnings,
        repositoryUrl = repoPath,
    )

    return listOf(
        gemmaModel(
            idSuffix = "q8-k-p",
            fileName = "Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-Q8_K_P.gguf",
            sizeLabel = "4.7 GB",
            ramEstimateMb = 12288,
            quantTag = "Q8_K_P"
        ),
        gemmaModel(
            idSuffix = "q6-k-p",
            fileName = "Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-Q6_K_P.gguf",
            sizeLabel = "3.7 GB",
            ramEstimateMb = 9216,
            quantTag = "Q6_K_P"
        ),
        gemmaModel(
            idSuffix = "q5-k-p",
            fileName = "Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-Q5_K_P.gguf",
            sizeLabel = "3.5 GB",
            ramEstimateMb = 8192,
            quantTag = "Q5_K_P"
        ),
        gemmaModel(
            idSuffix = "q4-k-p",
            fileName = "Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-Q4_K_P.gguf",
            sizeLabel = "3.3 GB",
            ramEstimateMb = 7168,
            quantTag = "Q4_K_P"
        ),
        gemmaModel(
            idSuffix = "q3-k-p",
            fileName = "Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-Q3_K_P.gguf",
            sizeLabel = "3.1 GB",
            ramEstimateMb = 6656,
            quantTag = "Q3_K_P"
        ),
        gemmaModel(
            idSuffix = "iq3-m",
            fileName = "Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-IQ3_M.gguf",
            sizeLabel = "3.0 GB",
            ramEstimateMb = 6144,
            quantTag = "IQ3_M"
        ),
        gemmaModel(
            idSuffix = "q2-k-p",
            fileName = "Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-Q2_K_P.gguf",
            sizeLabel = "2.9 GB",
            ramEstimateMb = 5632,
            quantTag = "Q2_K_P"
        ),
        HuggingFaceModel(
            id = "gemma4-e2b-mmproj-f16",
            name = "mmproj-Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-f16",
            description = "Gemma 4 E2B mmproj projector for VLM and image reasoning.",
            fileUri = "$repoPath/resolve/main/mmproj-Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-f16.gguf",
            approximateSize = "986 MB",
            modelType = ModelType.GGUF,
            isZip = false,
            runOnCpu = true,
            tags = listOf("GGUF", "mmproj", "vision", "Experimental"),
            displayNameRu = "mmproj Gemma 4 E2B",
            descriptionRu = "Проектор для Gemma 4 E2B. Нужен отдельно для VLM, фото-вопросов и live-камеры.",
            ramEstimateMb = 2048,
            supportStatus = ModelSupportStatus.EXPERIMENTAL,
            assistantEligible = false,
            liveEligible = true,
            experimental = true,
            source = CatalogSource.HUGGING_FACE.wireName,
            sourceLabel = CatalogSource.HUGGING_FACE.displayName,
            pageUrl = pageUrl,
            resolvedFileName = "mmproj-Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-f16.gguf",
            capabilities = listOf("assistant_live"),
            familyTags = commonFamilyTags + "mmproj",
            rawAssetOnly = true,
            downloadability = Downloadability.RAW_ASSET_DOWNLOAD,
            warnings = listOf(
                CatalogWarning(
                    title = "Нужен для картинок и live",
                    message = "Это mmproj projector для Gemma 4 E2B. Скачайте его после основной GGUF-модели, если хотите фото-вопросы, VLM и live-камеру.",
                    severity = "warning"
                )
            ),
            repositoryUrl = repoPath,
        )
    )
}

private class HuggingFaceCatalogAdapter(
    private val client: OkHttpClient,
) : CatalogSourceAdapter {
    override val source = CatalogSource.HUGGING_FACE

    override suspend fun search(query: String, filters: CatalogSearchFilters, limit: Int): List<HuggingFaceModel> {
        val url = "https://huggingface.co/api/models".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("limit", limit.coerceAtMost(50).toString())
            .addQueryParameter("full", "true")
            .build()

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body.string()
            val array = JSONArray(body)
            return buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val repoId = item.optString("id").ifBlank { continue }
                    val siblings = item.optJSONArray("siblings")
                    val filePaths = extractSiblingPaths(siblings)
                    val mapped = mapHuggingFaceModel(repoId, item, filePaths)
                    if (mapped != null) add(mapped)
                }
            }
        }
    }

    private fun extractSiblingPaths(siblings: JSONArray?): List<String> {
        if (siblings == null) return emptyList()
        return buildList {
            for (i in 0 until siblings.length()) {
                val sibling = siblings.optJSONObject(i) ?: continue
                val path = sibling.optString("rfilename").ifBlank { sibling.optString("path") }
                if (path.isNotBlank()) add(path)
            }
        }
    }

    private fun mapHuggingFaceModel(
        repoId: String,
        item: JSONObject,
        filePaths: List<String>,
    ): HuggingFaceModel? {
        val tags = mutableListOf<String>().apply {
            val tagArray = item.optJSONArray("tags")
            if (tagArray != null) {
                for (i in 0 until tagArray.length()) {
                    tagArray.optString(i)?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        val name = repoId.substringAfterLast("/")
        val description = item.optString("description").ifBlank { "$name model from Hugging Face." }
        val modelType = when {
            filePaths.any { it.endsWith(".gguf", ignoreCase = true) && !it.contains("mmproj", ignoreCase = true) } -> ModelType.GGUF
            tags.any { it.contains("text-to-speech", ignoreCase = true) || it.equals("tts", ignoreCase = true) } -> ModelType.TTS
            filePaths.any { it.endsWith(".onnx", ignoreCase = true) } -> ModelType.GGUF
            filePaths.any { it.endsWith(".zip", ignoreCase = true) || it.endsWith(".safetensors", ignoreCase = true) || it.endsWith(".ckpt", ignoreCase = true) } -> ModelType.SD
            else -> ModelType.GGUF
        }
        val chosenFile = when (modelType) {
            ModelType.GGUF -> filePaths.firstOrNull { it.endsWith(".gguf", ignoreCase = true) && !it.contains("mmproj", ignoreCase = true) }
                ?: filePaths.firstOrNull { it.endsWith(".onnx", ignoreCase = true) }
            ModelType.SD -> filePaths.firstOrNull { it.endsWith(".zip", ignoreCase = true) }
                ?: filePaths.firstOrNull { it.endsWith(".safetensors", ignoreCase = true) || it.endsWith(".ckpt", ignoreCase = true) }
            ModelType.TTS -> filePaths.firstOrNull()
        }
        val capabilities = UniversalCatalogRepository.inferCapabilities(tags, name, description)
        val supportStatus = when {
            capabilities.any { it == "video_generation" } -> ModelSupportStatus.EXPERIMENTAL
            chosenFile?.endsWith(".onnx", ignoreCase = true) == true -> ModelSupportStatus.LOCAL
            chosenFile?.endsWith(".gguf", ignoreCase = true) == true -> ModelSupportStatus.LOCAL
            modelType == ModelType.SD && chosenFile?.endsWith(".zip", ignoreCase = true) == true -> ModelSupportStatus.LOCAL
            else -> ModelSupportStatus.CATALOG_ONLY
        }
        val thumbnailCandidates = listOf(
            "https://huggingface.co/$repoId/resolve/main/thumbnail.png",
            "https://huggingface.co/$repoId/resolve/main/thumbnail.jpg",
            "https://huggingface.co/$repoId/resolve/main/thumbnail.webp"
        )
        val downloadability = when {
            chosenFile.isNullOrBlank() -> Downloadability.UNRESOLVED
            supportStatus == ModelSupportStatus.LOCAL -> Downloadability.LOCAL_RUNNABLE
            else -> Downloadability.RAW_ASSET_DOWNLOAD
        }
        val familyTags = buildFamilyTags(name, description, tags)
        return HuggingFaceModel(
            id = "hf-$repoId",
            name = name,
            description = description,
            fileUri = chosenFile?.let { "$repoId/resolve/main/$it" }.orEmpty(),
            approximateSize = item.optString("modelSize").ifBlank { "Unknown" },
            modelType = modelType,
            isZip = chosenFile?.endsWith(".zip", ignoreCase = true) == true,
            runOnCpu = !tags.any { it.equals("NPU", ignoreCase = true) },
            tags = (tags + executionTagsFor(modelType, supportStatus, capabilities)).distinct(),
            repositoryUrl = repoId,
            thumbnailUrl = thumbnailCandidates.first(),
            supportStatus = supportStatus,
            assistantEligible = capabilities.any { it == "chat" || it == "tts" },
            liveEligible = capabilities.any { it == "object_detection" || it == "face_detection" || it == "assistant_live" },
            source = CatalogSource.HUGGING_FACE.wireName,
            sourceLabel = CatalogSource.HUGGING_FACE.displayName,
            pageUrl = "https://huggingface.co/$repoId",
            downloadUrlOverride = chosenFile?.let { "https://huggingface.co/$repoId/resolve/main/$it?download=true" },
            resolvedFileName = chosenFile,
            previewImages = thumbnailCandidates,
            capabilities = capabilities,
            familyTags = familyTags,
            rawAssetOnly = supportStatus != ModelSupportStatus.LOCAL,
            experimental = supportStatus == ModelSupportStatus.EXPERIMENTAL,
            downloadability = downloadability,
            warnings = when (downloadability) {
                Downloadability.LOCAL_RUNNABLE -> emptyList()
                Downloadability.RAW_ASSET_DOWNLOAD -> experimentalWarnings("Модель можно скачать, но локальный запуск не гарантирован на этом устройстве.")
                Downloadability.UNRESOLVED -> listOf(
                    CatalogWarning(
                        title = "Нужен другой asset",
                        message = "Для этой модели не найден прямой скачиваемый файл.",
                        severity = "warning"
                    )
                )
                Downloadability.TOKEN_REQUIRED -> tokenWarnings()
            },
        )
    }
}

private class CivitaiCatalogAdapter(
    private val client: OkHttpClient,
) : CatalogSourceAdapter {
    override val source = CatalogSource.CIVITAI

    override suspend fun search(query: String, filters: CatalogSearchFilters, limit: Int): List<HuggingFaceModel> {
        val url = "https://civitai.com/api/v1/models".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("limit", limit.coerceAtMost(40).toString())
            .build()

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body.string()
            val root = JSONObject(body)
            val items = root.optJSONArray("items") ?: return emptyList()
            return buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val id = item.optInt("id")
                    val name = item.optString("name").ifBlank { continue }
                    val description = item.optString("description").ifBlank { "$name model from Civitai." }
                    val type = item.optString("type").ifBlank { "Checkpoint" }
                    val versions = item.optJSONArray("modelVersions")
                    val firstVersion = versions?.optJSONObject(0)
                    val files = firstVersion?.optJSONArray("files")
                    val firstFile = files?.optJSONObject(0)
                    val downloadUrl = firstFile?.optString("downloadUrl")
                    val sizeKb = firstFile?.optJSONObject("sizeKB")?.optLong("value")
                    val size = if (sizeKb != null && sizeKb > 0) "${(sizeKb / 1024.0).toInt()} MB" else "Unknown"
                    val thumbnail = item.optJSONArray("images")?.optJSONObject(0)?.optString("url")
                    val capabilities = when {
                        type.contains("video", ignoreCase = true) || name.contains("video", ignoreCase = true) -> listOf("video_generation")
                        else -> listOf("image_generation")
                    }
                    val previewImages = buildList {
                        thumbnail?.takeIf { it.isNotBlank() }?.let(::add)
                    }
                    val supportStatus = if (capabilities.contains("video_generation")) ModelSupportStatus.EXPERIMENTAL else ModelSupportStatus.EXPERIMENTAL
                    add(
                        HuggingFaceModel(
                            id = "civitai-$id",
                            name = name,
                            description = description,
                            fileUri = "",
                            approximateSize = size,
                            modelType = ModelType.SD,
                            isZip = false,
                            runOnCpu = false,
                            tags = buildList {
                                add("Raw asset")
                                add("SD")
                                if (item.optBoolean("nsfw")) add("NSFW")
                            },
                            displayNameRu = name,
                            descriptionRu = description,
                            thumbnailUrl = thumbnail,
                            previewImages = previewImages,
                            supportStatus = supportStatus,
                            source = CatalogSource.CIVITAI.wireName,
                            sourceLabel = CatalogSource.CIVITAI.displayName,
                            pageUrl = "https://civitai.com/models/$id",
                            downloadUrlOverride = downloadUrl?.takeIf { it.isNotBlank() },
                            resolvedFileName = firstFile?.optString("name"),
                            capabilities = capabilities,
                            familyTags = buildFamilyTags(name, description, listOf(type)),
                            rawAssetOnly = true,
                            experimental = capabilities.contains("video_generation"),
                            downloadability = if (downloadUrl.isNullOrBlank()) Downloadability.UNRESOLVED else Downloadability.RAW_ASSET_DOWNLOAD,
                            warnings = if (downloadUrl.isNullOrBlank()) {
                                listOf(CatalogWarning("Нет asset", "Civitai не вернул прямую ссылку на скачивание для этой версии.", "warning"))
                            } else {
                                experimentalWarnings("Модель будет скачана как raw asset. Локальный запуск не гарантирован на этом устройстве.")
                            },
                        )
                    )
                }
            }
        }
    }
}

private class ModelScopeCatalogAdapter(
    private val client: OkHttpClient,
) : CatalogSourceAdapter {
    override val source = CatalogSource.MODELSCOPE

    override suspend fun search(query: String, filters: CatalogSearchFilters, limit: Int): List<HuggingFaceModel> {
        val url = "https://www.modelscope.cn/api/v1/models".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("Search", query)
            .addQueryParameter("PageNumber", "1")
            .addQueryParameter("PageSize", limit.coerceAtMost(30).toString())
            .build()

        val request = Request.Builder().url(url).build()
        val remoteResults = runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = response.body.string()
                val root = JSONObject(body)
                val items = root.optJSONArray("Data")
                    ?: root.optJSONObject("Data")?.optJSONArray("Models")
                    ?: root.optJSONArray("Models")
                    ?: JSONArray()
                buildList {
                    for (index in 0 until items.length()) {
                        val item = items.optJSONObject(index) ?: continue
                        val modelId = item.optString("ModelId")
                            .ifBlank { item.optString("modelId") }
                            .ifBlank { item.optString("Name") }
                        if (modelId.isBlank()) continue
                        val name = modelId.substringAfterLast("/")
                        val description = item.optString("Description")
                            .ifBlank { item.optString("description") }
                            .ifBlank { "$name model from ModelScope." }
                        val tags = listOfNotNull(item.optString("Task"), item.optString("task")).filter { it.isNotBlank() }
                        val pageUrl = "https://www.modelscope.cn/models/$modelId"
                        add(
                            HuggingFaceModel(
                                id = "modelscope-$modelId",
                                name = name,
                                description = description,
                                fileUri = "",
                                approximateSize = "Unknown",
                                modelType = if (description.contains("video", ignoreCase = true)) ModelType.SD else ModelType.GGUF,
                                isZip = false,
                                runOnCpu = true,
                                tags = tags,
                                displayNameRu = name,
                                descriptionRu = description,
                                supportStatus = if (description.contains("video", ignoreCase = true)) ModelSupportStatus.EXPERIMENTAL else ModelSupportStatus.CATALOG_ONLY,
                                source = CatalogSource.MODELSCOPE.wireName,
                                sourceLabel = CatalogSource.MODELSCOPE.displayName,
                                pageUrl = pageUrl,
                                capabilities = UniversalCatalogRepository.inferCapabilities(tags, name, description),
                                familyTags = buildFamilyTags(name, description, tags),
                                rawAssetOnly = true,
                                experimental = description.contains("video", ignoreCase = true),
                                downloadability = Downloadability.UNRESOLVED,
                                warnings = listOf(
                                    CatalogWarning(
                                        title = "Нужен ручной выбор asset",
                                        message = "ModelScope-карточка найдена, но прямой downloadable asset не был автоматически разрешён.",
                                        severity = "warning"
                                    )
                                ),
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())

        return if (remoteResults.isNotEmpty()) {
            remoteResults
        } else {
            UniversalCatalogRepository()
                .starterModels()
                .filter { it.source == CatalogSource.MODELSCOPE.wireName }
                .filter { it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
        }
    }
}

private class GitHubCatalogAdapter(
    private val client: OkHttpClient,
) : CatalogSourceAdapter {
    override val source = CatalogSource.GITHUB

    override suspend fun search(query: String, filters: CatalogSearchFilters, limit: Int): List<HuggingFaceModel> {
        val url = "https://api.github.com/search/repositories".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("q", "$query gguf OR onnx OR diffusion OR piper")
            .addQueryParameter("sort", "stars")
            .addQueryParameter("order", "desc")
            .addQueryParameter("per_page", limit.coerceAtMost(8).toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "SantiyaLocalAiHub")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val items = JSONObject(response.body.string()).optJSONArray("items") ?: return emptyList()
            return buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val fullName = item.optString("full_name").ifBlank { continue }
                    val release = fetchLatestRelease(fullName) ?: continue
                    val asset = selectModelAsset(release.optJSONArray("assets")) ?: continue
                    val assetName = asset.optString("name").ifBlank { continue }
                    val downloadUrl = asset.optString("browser_download_url").ifBlank { continue }
                    val description = item.optString("description").ifBlank { "$fullName model repository on GitHub." }
                    val avatarUrl = item.optJSONObject("owner")?.optString("avatar_url")?.takeIf { it.isNotBlank() }
                    val modelType = when {
                        assetName.endsWith(".onnx", ignoreCase = true) -> ModelType.GGUF
                        assetName.endsWith(".zip", ignoreCase = true) || assetName.endsWith(".safetensors", ignoreCase = true) || assetName.endsWith(".ckpt", ignoreCase = true) -> ModelType.SD
                        assetName.contains("piper", ignoreCase = true) -> ModelType.TTS
                        else -> ModelType.GGUF
                    }
                    val capabilities = UniversalCatalogRepository.inferCapabilities(
                        tags = listOf(fullName, assetName, item.optString("topics")),
                        name = fullName.substringAfterLast("/"),
                        description = description
                    )
                    val supportStatus = when {
                        assetName.endsWith(".gguf", ignoreCase = true) || assetName.endsWith(".onnx", ignoreCase = true) -> ModelSupportStatus.LOCAL
                        capabilities.any { it == "video_generation" } -> ModelSupportStatus.EXPERIMENTAL
                        else -> ModelSupportStatus.EXPERIMENTAL
                    }
                    add(
                        HuggingFaceModel(
                            id = "github-$fullName-$assetName",
                            name = fullName.substringAfterLast("/"),
                            description = description,
                            fileUri = "",
                            approximateSize = asset.optLong("size").takeIf { it > 0 }?.let { bytesToApproxMb(it) } ?: "Unknown",
                            modelType = modelType,
                            isZip = assetName.endsWith(".zip", ignoreCase = true),
                            runOnCpu = true,
                            tags = (executionTagsFor(modelType, supportStatus, capabilities) + listOf("GitHub")).distinct(),
                            displayNameRu = fullName.substringAfterLast("/"),
                            descriptionRu = description,
                            thumbnailUrl = avatarUrl,
                            previewImages = listOfNotNull(avatarUrl),
                            supportStatus = supportStatus,
                            source = CatalogSource.GITHUB.wireName,
                            sourceLabel = CatalogSource.GITHUB.displayName,
                            pageUrl = item.optString("html_url"),
                            downloadUrlOverride = downloadUrl,
                            resolvedFileName = assetName,
                            capabilities = capabilities,
                            familyTags = buildFamilyTags(fullName, description, listOf(assetName)),
                            rawAssetOnly = supportStatus != ModelSupportStatus.LOCAL,
                            experimental = supportStatus == ModelSupportStatus.EXPERIMENTAL,
                            downloadability = if (downloadUrl.isBlank()) Downloadability.UNRESOLVED else if (supportStatus == ModelSupportStatus.LOCAL) Downloadability.LOCAL_RUNNABLE else Downloadability.RAW_ASSET_DOWNLOAD,
                            warnings = if (supportStatus == ModelSupportStatus.LOCAL) emptyList() else experimentalWarnings("GitHub asset будет скачан напрямую из release. Локальный запуск зависит от формата и вашего устройства."),
                        )
                    )
                }
            }
        }
    }

    private fun fetchLatestRelease(fullName: String): JSONObject? {
        val url = "https://api.github.com/repos/$fullName/releases/latest".toHttpUrlOrNull() ?: return null
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "SantiyaLocalAiHub")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                JSONObject(response.body.string())
            }
        }.getOrNull()
    }

    private fun selectModelAsset(assets: JSONArray?): JSONObject? {
        if (assets == null) return null
        val preferredExtensions = listOf(".gguf", ".onnx", ".zip", ".safetensors", ".ckpt")
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (preferredExtensions.any { name.endsWith(it, ignoreCase = true) }) {
                return asset
            }
        }
        return null
    }
}

private fun experimentalWarnings(message: String): List<CatalogWarning> = listOf(
    CatalogWarning(
        title = "Высокий шанс, что не запустится",
        message = message,
        severity = "warning"
    )
)

private fun tokenWarnings(): List<CatalogWarning> = listOf(
    CatalogWarning(
        title = "Нужен токен",
        message = "Источник требует токен или авторизацию для скачивания.",
        severity = "warning"
    )
)

private fun buildFamilyTags(name: String, description: String, tags: List<String>): List<String> {
    val text = buildString {
        append(name).append(' ')
        append(description).append(' ')
        append(tags.joinToString(" "))
    }
    return buildList {
        if (text.contains("uncensored", ignoreCase = true)) add("Uncensored")
        if (text.contains("abliterated", ignoreCase = true)) add("Abliterated")
        if (text.contains("dolphin", ignoreCase = true)) add("Dolphin")
        if (text.contains("yolo", ignoreCase = true)) add("YOLO")
        if (text.contains("arcface", ignoreCase = true) || text.contains("mobilefacenet", ignoreCase = true)) add("Face")
        if (text.contains("video", ignoreCase = true)) add("Video")
        if (text.contains("piper", ignoreCase = true) || text.contains("russian", ignoreCase = true)) add("RU")
    }.distinct()
}

private fun executionTagsFor(
    modelType: ModelType,
    supportStatus: ModelSupportStatus,
    capabilities: List<String>,
): List<String> {
    val tags = mutableListOf<String>()
    when (supportStatus) {
        ModelSupportStatus.LOCAL -> tags += "CPU"
        ModelSupportStatus.LAN -> tags += "LAN"
        ModelSupportStatus.EXPERIMENTAL -> tags += "Experimental"
        ModelSupportStatus.CATALOG_ONLY -> tags += "Raw asset"
    }
    if (capabilities.any { it.contains("video", ignoreCase = true) }) tags += "video"
    if (capabilities.any { it.contains("face", ignoreCase = true) }) tags += "vision"
    if (modelType == ModelType.SD) tags += "diffusion"
    return tags.distinct()
}

private fun bytesToApproxMb(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) {
        "${"%.1f".format(Locale.US, mb / 1024.0)} GB"
    } else {
        "${mb.toInt()} MB"
    }
}
