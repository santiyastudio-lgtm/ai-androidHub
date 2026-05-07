package com.santiya.localaihub.worker

import com.arm.aichat.gguf.GgufMetadataReader
import java.io.File

enum class GgufRuntimeBackend {
    LEGACY_GGUF_LIB,
    SOURCE_AI_CHAT,
}

data class GgufRuntimeCompatibility(
    val architecture: String?,
    val supported: Boolean,
    val backend: GgufRuntimeBackend,
    val message: String? = null,
)

object GgufRuntimeSupport {
    suspend fun inspect(file: File): GgufRuntimeCompatibility {
        if (!file.exists() || !file.isFile) {
            return GgufRuntimeCompatibility(
                architecture = null,
                supported = false,
                backend = GgufRuntimeBackend.LEGACY_GGUF_LIB,
                message = "Файл модели не найден."
            )
        }

        return runCatching {
            val metadata = file.inputStream().buffered().use { input ->
                GgufMetadataReader.create().readStructuredMetadata(input)
            }
            val architecture = metadata.architecture?.architecture?.lowercase()
            GgufRuntimeCompatibility(
                architecture = architecture,
                supported = true,
                backend = preferredBackendFor(architecture)
            )
        }.getOrElse { error ->
            GgufRuntimeCompatibility(
                architecture = null,
                supported = false,
                backend = GgufRuntimeBackend.LEGACY_GGUF_LIB,
                message = error.message ?: "Не удалось прочитать метаданные GGUF."
            )
        }
    }

    fun preferredBackendFor(architecture: String?): GgufRuntimeBackend =
        when (architecture?.lowercase()) {
            // Gemma 4 requires the newer ai-chat/llama.cpp path. The legacy JNI runtime
            // cannot load the architecture at all ("unknown model architecture: gemma4").
            "gemma4" -> GgufRuntimeBackend.SOURCE_AI_CHAT
            else -> GgufRuntimeBackend.LEGACY_GGUF_LIB
        }

    fun unsupportedArchitectureMessage(architecture: String?): String {
        val arch = architecture?.ifBlank { "unknown" } ?: "unknown"
        return "GGUF runtime этой сборки не поддерживает архитектуру $arch."
    }
}
