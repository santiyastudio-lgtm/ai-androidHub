package com.santiya.localaihub.distributed

import kotlinx.serialization.Serializable

object DistributedProtocolIds {
    const val LIBP2P_PIPELINE = "/santiya/gguf/pipeline/1.0.0"
    const val LIBP2P_HIDDEN_STATE = "/santiya/gguf/hidden-state/1.0.0"
    const val LIBP2P_NODE_CONTROL = "/santiya/gguf/node-control/1.0.0"

    const val HTTP_STATUS = "/hub/distributed/status"
    const val HTTP_DISTRIBUTED_PLAN = "/hub/distributed/plan"
    const val HTTP_HANDSHAKE = "/hub/distributed/handshake"
    const val HTTP_PIPELINE_STEP = "/hub/distributed/pipeline-step"
    const val HTTP_HIDDEN_STATE = "/hub/distributed/hidden-state"
    const val HTTP_EXECUTE = "/hub/lan/execute"
}

@Serializable
data class DistributedSessionHandshake(
    val sessionId: String,
    val modelId: String,
    val rootNodeId: String,
    val totalLayers: Int,
    val assignedRange: GgufLayerRange,
    val kvCacheCompression: KvCacheCompressionMode,
    val transport: DistributedTransportProtocol,
    val protocolVersion: String = "1.0.0"
)

@Serializable
data class HiddenStateTensorDescriptor(
    val dtype: String,
    val shape: List<Int>,
    val tokenCount: Int,
    val rawBytes: Int,
    val compressedBytes: Int,
    val compression: KvCacheCompressionMode
)

@Serializable
data class HiddenStateEnvelope(
    val sessionId: String,
    val stepId: Long,
    val fromNodeId: String,
    val toNodeId: String,
    val layerRange: GgufLayerRange,
    val tensor: HiddenStateTensorDescriptor,
    val payloadBase64: String? = null,
    val payloadRef: String? = null
)

@Serializable
data class PipelineStepRequest(
    val sessionId: String,
    val stepId: Long,
    val modelId: String,
    val assignedRange: GgufLayerRange,
    val hiddenState: HiddenStateEnvelope,
    val kvCacheRef: String? = null,
    val maxTokens: Int = 1
)

@Serializable
data class PipelineStepResponse(
    val ok: Boolean,
    val sessionId: String,
    val stepId: Long,
    val nodeId: String,
    val producedState: HiddenStateEnvelope? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class NodeAnnouncement(
    val node: NodeResourceSnapshot,
    val supportedModels: List<String> = emptyList(),
    val protocols: List<String> = emptyList(),
    val pairingRequired: Boolean = true
)
