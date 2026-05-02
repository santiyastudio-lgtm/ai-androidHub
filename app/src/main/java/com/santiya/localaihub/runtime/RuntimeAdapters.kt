package com.santiya.localaihub.runtime

import kotlinx.serialization.Serializable

interface RuntimeAdapter {
    val runtimeName: String
    val supportedCapabilities: Set<ModelCapability>
    val state: RuntimeState
    fun describe(): RuntimeCapabilityInfo
}

class GgufChatAdapter : RuntimeAdapter {
    override val runtimeName = "gguf"
    override val supportedCapabilities = setOf(ModelCapability.CHAT, ModelCapability.RAG)
    override val state = RuntimeState.READY
    override fun describe() = RuntimeCapabilityInfo(
        capability = ModelCapability.CHAT,
        runtime = runtimeName,
        state = state,
        description = "GGUF chat runtime backed by the existing LLMService generation methods."
    )
}

class DiffusionImageAdapter : RuntimeAdapter {
    override val runtimeName = "stable-diffusion"
    override val supportedCapabilities = setOf(ModelCapability.IMAGE_GENERATION)
    override val state = RuntimeState.READY
    override fun describe() = RuntimeCapabilityInfo(
        capability = ModelCapability.IMAGE_GENERATION,
        runtime = runtimeName,
        state = state,
        description = "Stable Diffusion runtime backed by the existing diffusion service methods."
    )
}

class OnnxVisionAdapter : RuntimeAdapter {
    override val runtimeName = "onnxruntime"
    override val supportedCapabilities = setOf(ModelCapability.OBJECT_DETECTION, ModelCapability.EMBEDDINGS)
    override val state = RuntimeState.NEEDS_MODEL
    override fun describe() = RuntimeCapabilityInfo(
        capability = ModelCapability.OBJECT_DETECTION,
        runtime = runtimeName,
        state = state,
        description = "ONNX Runtime adapter contract for object detection and embeddings."
    )
}

class SegmentationAdapter : RuntimeAdapter {
    override val runtimeName = "segmentation"
    override val supportedCapabilities = setOf(ModelCapability.IMAGE_SEGMENTATION)
    override val state = RuntimeState.NEEDS_MODEL
    override fun describe() = RuntimeCapabilityInfo(
        capability = ModelCapability.IMAGE_SEGMENTATION,
        runtime = runtimeName,
        state = state,
        description = "Segmentation adapter contract for MobileSAM or compatible ONNX models."
    )
}

class TtsAdapter : RuntimeAdapter {
    override val runtimeName = "tts"
    override val supportedCapabilities = setOf(ModelCapability.TTS)
    override val state = RuntimeState.READY
    override fun describe() = RuntimeCapabilityInfo(
        capability = ModelCapability.TTS,
        runtime = runtimeName,
        state = state,
        description = "On-device TTS runtime backed by the bundled speech engine."
    )
}

class ExperimentalVideoAdapter : RuntimeAdapter {
    override val runtimeName = "experimental-video"
    override val supportedCapabilities = setOf(ModelCapability.VIDEO_GENERATION)
    override val state = RuntimeState.EXPERIMENTAL
    override fun describe() = RuntimeCapabilityInfo(
        capability = ModelCapability.VIDEO_GENERATION,
        runtime = runtimeName,
        state = state,
        description = "Catalog and API placeholder; requires an installed Android video generation backend."
    )
}

class Experimental3dAdapter : RuntimeAdapter {
    override val runtimeName = "experimental-3d"
    override val supportedCapabilities = setOf(ModelCapability.THREE_D_GENERATION)
    override val state = RuntimeState.EXPERIMENTAL
    override fun describe() = RuntimeCapabilityInfo(
        capability = ModelCapability.THREE_D_GENERATION,
        runtime = runtimeName,
        state = state,
        description = "Catalog and API placeholder; requires an installed Android 3D generation backend."
    )
}

@Serializable
data class RuntimeJob(
    val id: String,
    val capability: ModelCapability,
    val modelId: String?,
    val createdAtMs: Long
)

@Serializable
data class RuntimeEvent(
    val jobId: String,
    val type: String,
    val payloadJson: String
)
