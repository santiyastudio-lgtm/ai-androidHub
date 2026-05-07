package com.santiya.googlelocalruntime

enum class GoogleLocalRuntimeType {
    LITERT_LM,
    AICORE
}

enum class GoogleLocalAvailabilityState {
    AVAILABLE,
    DOWNLOADABLE,
    DOWNLOADING,
    UNAVAILABLE,
    UNKNOWN
}

data class GoogleLocalModelDescriptor(
    val modelId: String,
    val modelName: String,
    val runtimeType: GoogleLocalRuntimeType,
    val localPath: String? = null,
    val maxTokens: Int = 1024,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val temperature: Float = 1.0f,
    val accelerator: String = "cpu",
    val supportImage: Boolean = false,
    val aicorePreference: String = "fast",
    val aicoreReleaseStage: String = "stable",
)

data class GoogleLocalMessage(
    val role: String,
    val content: String
)

sealed class GoogleLocalGenerationEvent {
    data class Token(val text: String) : GoogleLocalGenerationEvent()
    data object Done : GoogleLocalGenerationEvent()
    data class Error(val message: String) : GoogleLocalGenerationEvent()
}
