package com.santiya.localaihub.distributed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DistributedTransportProtocol {
    @SerialName("nsd_http")
    NSD_HTTP,

    @SerialName("nsd_libp2p")
    NSD_LIBP2P,

    @SerialName("grpc")
    GRPC,

    @SerialName("libp2p")
    LIBP2P
}

@Serializable
enum class DistributedNodePlatform {
    @SerialName("android")
    ANDROID,

    @SerialName("macos")
    MACOS,

    @SerialName("windows")
    WINDOWS,

    @SerialName("linux")
    LINUX,

    @SerialName("unknown")
    UNKNOWN
}

@Serializable
enum class DistributedExecutionStrategy {
    @SerialName("local_only")
    LOCAL_ONLY,

    @SerialName("sequential_offload")
    SEQUENTIAL_OFFLOAD,

    @SerialName("pipeline_parallel_lan")
    PIPELINE_PARALLEL_LAN
}

@Serializable
enum class KvCacheCompressionMode {
    @SerialName("none")
    NONE,

    @SerialName("q8")
    Q8,

    @SerialName("q6")
    Q6,

    @SerialName("q4")
    Q4
}

@Serializable
data class GgufLayerRange(
    val startLayerInclusive: Int,
    val endLayerExclusive: Int
) {
    val layerCount: Int
        get() = (endLayerExclusive - startLayerInclusive).coerceAtLeast(0)
}

@Serializable
data class GgufModelProfile(
    val modelId: String,
    val modelName: String,
    val architecture: String,
    val estimatedParameterScaleB: Double?,
    val layerCount: Int,
    val hiddenSize: Int,
    val attentionHeads: Int,
    val kvHeads: Int,
    val contextLength: Int,
    val modelSizeMb: Int,
    val estimatedLayerFootprintMb: Double,
    val estimatedHiddenStateBytesPerToken: Int,
    val heuristicProfile: Boolean,
    val notes: List<String> = emptyList()
)

@Serializable
data class NodeResourceSnapshot(
    val nodeId: String,
    val displayName: String,
    val platform: DistributedNodePlatform,
    val totalRamMb: Int,
    val freeRamMb: Int,
    val cpuCores: Int,
    val cpuArch: String,
    val acceleratorSummary: String,
    val acceleratorScore: Double,
    val computeScore: Double,
    val availableStorageMb: Int,
    val supportsSequentialOffload: Boolean,
    val supportsPipelineWorker: Boolean,
    val heavySlotAvailable: Boolean,
    val transportProtocols: List<DistributedTransportProtocol>,
    val lastSeenEpochMs: Long,
    val notes: List<String> = emptyList()
)

@Serializable
data class SequentialOffloadWindow(
    val phaseIndex: Int,
    val layerRange: GgufLayerRange,
    val targetDevice: String = "local"
)

@Serializable
data class SequentialOffloadPlan(
    val enabled: Boolean,
    val residentLayerWindow: Int,
    val estimatedPeakRamMb: Int,
    val estimatedKvCacheMb: Int,
    val estimatedFlashTrafficMbPerToken: Double,
    val kvCacheCompression: KvCacheCompressionMode,
    val windows: List<SequentialOffloadWindow>,
    val notes: List<String> = emptyList()
)

@Serializable
data class HiddenStateTransferPlan(
    val compression: KvCacheCompressionMode,
    val rawBytesPerToken: Int,
    val compressedBytesPerToken: Int,
    val preferredTransport: DistributedTransportProtocol,
    val notes: List<String> = emptyList()
)

@Serializable
data class PipelineSegmentAssignment(
    val nodeId: String,
    val nodeName: String,
    val layerRange: GgufLayerRange,
    val role: String,
    val estimatedResidentMb: Int
)

@Serializable
data class DistributedPartitionPlan(
    val strategy: DistributedExecutionStrategy,
    val modelProfile: GgufModelProfile,
    val localNode: NodeResourceSnapshot,
    val peerNodes: List<NodeResourceSnapshot>,
    val assignments: List<PipelineSegmentAssignment>,
    val sequentialOffloadPlan: SequentialOffloadPlan? = null,
    val hiddenStateTransferPlan: HiddenStateTransferPlan? = null,
    val nativeExecutionRequired: Boolean = true,
    val requiredEngineHooks: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val notes: List<String> = emptyList()
)

@Serializable
data class DistributedPlanEnvelope(
    val ok: Boolean,
    val modelId: String? = null,
    val plan: DistributedPartitionPlan? = null,
    val error: String? = null,
    val message: String? = null
)

interface PeerDiscoveryService {
    fun start()
    fun stop()
    fun snapshot(): List<NodeResourceSnapshot>
}

interface TensorTransport {
    val protocol: DistributedTransportProtocol
    fun isAvailable(): Boolean
}
