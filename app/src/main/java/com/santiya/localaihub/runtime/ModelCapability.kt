package com.santiya.localaihub.runtime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ModelCapability {
    @SerialName("chat")
    CHAT,

    @SerialName("embeddings")
    EMBEDDINGS,

    @SerialName("object_detection")
    OBJECT_DETECTION,

    @SerialName("image_segmentation")
    IMAGE_SEGMENTATION,

    @SerialName("image_generation")
    IMAGE_GENERATION,

    @SerialName("upscale")
    UPSCALE,

    @SerialName("tts")
    TTS,

    @SerialName("rag")
    RAG,

    @SerialName("video_generation")
    VIDEO_GENERATION,

    @SerialName("3d_generation")
    THREE_D_GENERATION
}

@Serializable
enum class RuntimeState {
    READY,
    NEEDS_MODEL,
    EXPERIMENTAL,
    UNSUPPORTED
}
