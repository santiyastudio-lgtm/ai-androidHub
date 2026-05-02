package com.santiya.localaihub.hub

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThemePreset {
    SYSTEM,
    MIDNIGHT_VIOLET,
    OBSIDIAN_MONO,
    MARBLE_LILAC
}

@Serializable
enum class ModelSupportStatus {
    @SerialName("local")
    LOCAL,

    @SerialName("lan")
    LAN,

    @SerialName("experimental")
    EXPERIMENTAL,

    @SerialName("catalog_only")
    CATALOG_ONLY
}

@Serializable
enum class Downloadability {
    @SerialName("local_runnable")
    LOCAL_RUNNABLE,

    @SerialName("raw_asset_download")
    RAW_ASSET_DOWNLOAD,

    @SerialName("token_required")
    TOKEN_REQUIRED,

    @SerialName("unresolved")
    UNRESOLVED
}

@Serializable
data class CatalogWarning(
    val title: String,
    val message: String,
    val severity: String = "info"
)

@Serializable
enum class HubRuntimeState {
    SLEEPING,
    WAKING,
    IDLE,
    DOWNLOADING,
    GENERATING,
    LAN_OFFLOAD_ACTIVE,
    ORCHESTRA_ACTIVE,
    SERVING_EXTERNAL_APP,
    ERROR
}

@Serializable
data class CatalogPresentationEntry(
    val id: String,
    val titleRu: String,
    val descriptionRu: String,
    val taskLabelRu: String,
    val thumbnailUrl: String? = null,
    val previewImages: List<String> = emptyList(),
    val ramEstimateMb: Int? = null,
    val supportStatus: ModelSupportStatus = ModelSupportStatus.LOCAL,
    val downloadability: Downloadability = Downloadability.LOCAL_RUNNABLE,
    val warnings: List<CatalogWarning> = emptyList(),
    val sourceLabel: String = "Hugging Face",
    val assistantEligible: Boolean = false,
    val liveEligible: Boolean = false,
    val tagsRu: List<String> = emptyList()
)

@Serializable
data class PreferredModelMap(
    val chatModelId: String? = null,
    val visionModelId: String? = null,
    val imageGenerationModelId: String? = null,
    val videoGenerationModelId: String? = null,
    val ttsModelId: String? = null,
    val filesModelId: String? = null,
    val assistantLiveModelId: String? = null
)

@Serializable
data class ApprovedClientApp(
    val packageName: String,
    val appLabel: String,
    val approvedAtEpochMs: Long,
    val clientId: String = "",
    val apiKeyPreview: String = "",
    val apiKeyHash: String = "",
    val scopes: List<String> = emptyList()
)

@Serializable
data class ExternalAccessPolicy(
    val enabled: Boolean = false,
    val approvedApps: List<ApprovedClientApp> = emptyList(),
    val pendingPackages: List<String> = emptyList()
)

@Serializable
enum class OrchestraRole {
    ROUTER,
    CHAT_SPECIALIST,
    FILES_SPECIALIST,
    VISION_SPECIALIST,
    CODE_SPECIALIST,
    SUMMARY_TTS
}

@Serializable
enum class OrchestraExecutionMode {
    SINGLE_MODEL,
    SMALL_MODEL_ORCHESTRA
}

@Serializable
data class OrchestraConfig(
    val enabled: Boolean = false,
    val autoAssign: Boolean = true,
    val allowLanSpillover: Boolean = true,
    val assignedModels: Map<String, String> = emptyMap()
)

@Serializable
data class OrchestraCapabilityState(
    val mode: OrchestraExecutionMode = OrchestraExecutionMode.SINGLE_MODEL,
    val supported: Boolean = false,
    val eligibleModelIds: List<String> = emptyList(),
    val reason: String = ""
)

@Serializable
data class LanHubConfig(
    val enabled: Boolean = false,
    val advertiseLocalNode: Boolean = true,
    val pairingToken: String = ""
)

@Serializable
data class LanNodeInfo(
    val id: String,
    val name: String,
    val isLocal: Boolean,
    val status: String,
    val capabilities: List<String>,
    val installedModelCount: Int,
    val notes: String? = null,
    val platform: String = "android",
    val totalRamMb: Int? = null,
    val freeRamMb: Int? = null,
    val cpuCores: Int? = null,
    val acceleratorSummary: String? = null,
    val computeScore: Double? = null,
    val transportProtocols: List<String> = emptyList(),
    val heavySlotAvailable: Boolean = true,
    val supportsSequentialOffload: Boolean = false,
    val supportsPipelineWorker: Boolean = false,
    val lastSeenEpochMs: Long? = null
)

@Serializable
data class LanJobRequest(
    val capability: String,
    val mode: OrchestraExecutionMode = OrchestraExecutionMode.SINGLE_MODEL,
    val modelId: String? = null,
    val payloadJson: String
)

@Serializable
data class LanJobResult(
    val ok: Boolean,
    val status: String,
    val message: String,
    val nodeId: String? = null,
    val resultJson: String? = null
)
