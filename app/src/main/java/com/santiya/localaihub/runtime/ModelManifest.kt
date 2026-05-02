package com.santiya.localaihub.runtime

import kotlinx.serialization.Serializable

@Serializable
data class ModelManifest(
    val id: String,
    val name: String,
    val source: ModelSource,
    val files: List<ModelFile>,
    val capabilities: List<ModelCapability>,
    val runtime: String,
    val license: String? = null,
    val minRamMb: Int? = null,
    val tags: List<String> = emptyList(),
    val experimental: Boolean = false,
    val notes: String? = null
)

@Serializable
data class ModelSource(
    val type: String,
    val repo: String? = null,
    val url: String? = null
)

@Serializable
data class ModelFile(
    val path: String,
    val sizeBytes: Long? = null,
    val sha256: String? = null,
    val role: String = "model"
)

@Serializable
data class RuntimeCapabilityInfo(
    val capability: ModelCapability,
    val runtime: String,
    val state: RuntimeState,
    val description: String
)

@Serializable
data class RuntimeStatusResponse(
    val app: String = "SantiyaLocalAiHub",
    val packageName: String = "com.santiya.localaihub",
    val capabilities: List<RuntimeCapabilityInfo>,
    val httpApi: HttpApiState = HttpApiState()
)

@Serializable
data class HttpApiState(
    val enabled: Boolean = false,
    val bindAddress: String = "127.0.0.1",
    val port: Int? = null,
    val requiresToken: Boolean = true
)
