package com.santiya.localaihub.service

import com.santiya.localaihub.hub.ModelCatalogPresentation
import com.santiya.localaihub.hub.CatalogWarning
import com.santiya.localaihub.hub.Downloadability
import com.santiya.localaihub.hub.ModelSupportStatus
import com.santiya.localaihub.models.data.HuggingFaceModel
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class ExternalCatalogModelDescriptor(
    val id: String,
    val displayName: String,
    val version: String,
    val downloadUrl: String,
    val sha256: String,
    val signature: String,
    val sizeBytes: Long,
    val minimumRamBytes: Long,
    val recommendedSdkInt: Int,
    val licenseUrl: String,
    val source: String = "huggingface",
    val supportStatus: String = "local",
    val familyTags: List<String> = emptyList(),
    val capabilities: List<String> = emptyList(),
    val promptLanguage: String = "en",
    val previewImages: List<String> = emptyList(),
    val downloadability: String = Downloadability.UNRESOLVED.name.lowercase(Locale.US),
    val warnings: List<CatalogWarning> = emptyList(),
    val resolvedFileName: String? = null,
    val runnableLocally: Boolean = false,
    val recommendedExecutionTarget: String = "local",
)

@Serializable
data class ExternalDownloadRequest(
    val modelId: String = "",
    val capability: String = "",
    val allowMetered: Boolean = false,
    val auth: HubAuthEnvelope? = null,
)

@Serializable
data class ExternalDownloadResponse(
    val ok: Boolean,
    val status: String,
    val modelId: String? = null,
    val message: String,
)

@Serializable
data class ExternalDownloadStatusResponse(
    val status: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val progressPercent: Float,
    val installed: Boolean,
    val selected: Boolean,
    val message: String,
)

@Serializable
data class ExternalPreparePreferredResponse(
    val ok: Boolean,
    val modelId: String? = null,
    val runtime: String? = null,
    val message: String,
)

@Serializable
data class HubAuthEnvelope(
    val clientId: String = "",
    val apiKey: String = "",
    val packageName: String = "",
)

@Serializable
data class CatalogSearchRequest(
    val query: String = "",
    val sources: List<String> = emptyList(),
    val capabilities: List<String> = emptyList(),
    val familyTags: List<String> = emptyList(),
    val pageToken: String? = null,
    val auth: HubAuthEnvelope? = null,
)

@Serializable
data class HubExecutionRequest(
    val capability: String = "chat",
    val modelId: String? = null,
    val systemPrompt: String? = null,
    val inputJson: String = "",
    val orchestrationMode: String = "single_model",
    val auth: HubAuthEnvelope? = null,
)

@Serializable
data class FaceRecognitionRequest(
    val detectorModelId: String? = null,
    val embedderModelId: String? = null,
    val imageRef: String = "",
    val referenceEmbeddingsJson: String = "[]",
    val threshold: Float = 0.85f,
    val auth: HubAuthEnvelope? = null,
)

@Serializable
data class VideoGenerationRequest(
    val modelId: String? = null,
    val prompt: String = "",
    val negativePrompt: String? = null,
    val mode: String = "single_model",
    val auth: HubAuthEnvelope? = null,
)

@Serializable
data class LiveSessionRequest(
    val mode: String = "",
    val modelId: String? = null,
    val auth: HubAuthEnvelope? = null,
)

@Serializable
data class ExternalClientRegistrationRequest(
    val packageName: String = "",
    val appLabel: String? = null,
    val scopes: List<String> = emptyList(),
)

@Serializable
data class ExternalClientRegistrationResponse(
    val ok: Boolean,
    val packageName: String,
    val clientId: String? = null,
    val apiKey: String? = null,
    val approved: Boolean = false,
    val scopes: List<String> = emptyList(),
    val message: String,
)

internal fun HuggingFaceModel.toExternalCatalogDescriptor(locale: String?): ExternalCatalogModelDescriptor {
    val presentation = ModelCatalogPresentation.presentationFor(this)
    val normalizedLocale = locale?.lowercase(Locale.US).orEmpty()
    val displayName = if (normalizedLocale.startsWith("ru")) {
        presentation.titleRu.ifBlank { name }
    } else {
        name
    }
    val sizeBytes = parseApproximateSizeBytes(approximateSize)
    val minimumRamMb = presentation.ramEstimateMb ?: 4096
    val promptLanguage = if (tags.any { it.equals("Multilingual", ignoreCase = true) }) "multilingual" else "en"
    return ExternalCatalogModelDescriptor(
        id = id,
        displayName = displayName,
        version = name,
        downloadUrl = downloadUrlOverride ?: "hub://models/$id",
        sha256 = "hub-managed",
        signature = "hub-managed",
        sizeBytes = sizeBytes,
        minimumRamBytes = minimumRamMb * 1024L * 1024L,
        recommendedSdkInt = 29,
        licenseUrl = repositoryUrl.takeIf { it.isNotBlank() }?.let { "https://huggingface.co/$it" }
            ?: pageUrl
            ?: "https://huggingface.co",
        source = source,
        supportStatus = (supportStatus ?: ModelSupportStatus.LOCAL).name.lowercase(Locale.US),
        familyTags = familyTags,
        capabilities = capabilities,
        promptLanguage = promptLanguage,
        previewImages = previewImages,
        downloadability = downloadability.name.lowercase(Locale.US),
        warnings = warnings,
        resolvedFileName = resolvedFileName,
        runnableLocally = downloadability == Downloadability.LOCAL_RUNNABLE,
        recommendedExecutionTarget = when {
            downloadability == Downloadability.LOCAL_RUNNABLE -> "local"
            capabilities.any { it.contains("video", ignoreCase = true) } -> "lan"
            rawAssetOnly -> "raw_asset"
            else -> "local"
        },
    )
}

internal fun mapDownloadStatusResponse(
    state: ModelDownloadService.DownloadState?,
    installed: Boolean,
    selected: Boolean,
    installedBytes: Long,
): ExternalDownloadStatusResponse {
    return when (state) {
        is ModelDownloadService.DownloadState.Downloading -> ExternalDownloadStatusResponse(
            status = "downloading",
            bytesDownloaded = state.downloadedBytes,
            totalBytes = state.totalBytes,
            progressPercent = state.progress * 100f,
            installed = installed,
            selected = selected,
            message = "Downloading in AI Hub",
        )

        is ModelDownloadService.DownloadState.Extracting -> ExternalDownloadStatusResponse(
            status = "extracting",
            bytesDownloaded = state.extractedCount.toLong(),
            totalBytes = state.totalFiles.toLong(),
            progressPercent = if (state.totalFiles > 0) {
                (state.extractedCount.toFloat() / state.totalFiles.toFloat()) * 100f
            } else {
                0f
            },
            installed = installed,
            selected = selected,
            message = if (state.currentFile.isBlank()) "Extracting in AI Hub" else "Extracting ${state.currentFile}",
        )

        is ModelDownloadService.DownloadState.Processing -> ExternalDownloadStatusResponse(
            status = "processing",
            bytesDownloaded = installedBytes,
            totalBytes = installedBytes,
            progressPercent = if (installed) 100f else 0f,
            installed = installed,
            selected = selected,
            message = "Processing in AI Hub",
        )

        is ModelDownloadService.DownloadState.Success -> ExternalDownloadStatusResponse(
            status = "ready",
            bytesDownloaded = installedBytes,
            totalBytes = installedBytes,
            progressPercent = 100f,
            installed = installed,
            selected = selected,
            message = "Installed in AI Hub",
        )

        is ModelDownloadService.DownloadState.Error -> ExternalDownloadStatusResponse(
            status = "failed",
            bytesDownloaded = 0,
            totalBytes = installedBytes,
            progressPercent = 0f,
            installed = installed,
            selected = selected,
            message = state.message,
        )

        is ModelDownloadService.DownloadState.Cancelled -> ExternalDownloadStatusResponse(
            status = "canceled",
            bytesDownloaded = 0,
            totalBytes = installedBytes,
            progressPercent = 0f,
            installed = installed,
            selected = selected,
            message = "Canceled in AI Hub",
        )

        null -> ExternalDownloadStatusResponse(
            status = if (installed) "ready" else "idle",
            bytesDownloaded = if (installed) installedBytes else 0,
            totalBytes = if (installed) installedBytes else 0,
            progressPercent = if (installed) 100f else 0f,
            installed = installed,
            selected = selected,
            message = if (installed) "Installed in AI Hub" else "Not installed in AI Hub",
        )
    }
}

internal fun parseApproximateSizeBytes(raw: String): Long {
    val match = Regex("""([\d.]+)\s*([KMGTP]?B)""", RegexOption.IGNORE_CASE).find(raw) ?: return 0L
    val value = match.groupValues[1].toDoubleOrNull() ?: return 0L
    val unit = match.groupValues[2].uppercase(Locale.US)
    val multiplier = when (unit) {
        "KB" -> 1024.0
        "MB" -> 1024.0 * 1024.0
        "GB" -> 1024.0 * 1024.0 * 1024.0
        "TB" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
        else -> 1.0
    }
    return (value * multiplier).toLong()
}
