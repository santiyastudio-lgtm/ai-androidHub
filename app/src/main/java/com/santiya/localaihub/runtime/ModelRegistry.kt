package com.santiya.localaihub.runtime

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ModelRegistry {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun capabilities(): List<RuntimeCapabilityInfo> = listOf(
        RuntimeCapabilityInfo(
            capability = ModelCapability.CHAT,
            runtime = "gguf",
            state = RuntimeState.READY,
            description = "Local GGUF chat and tool-calling through the bundled llama.cpp runtime."
        ),
        RuntimeCapabilityInfo(
            capability = ModelCapability.IMAGE_GENERATION,
            runtime = "stable-diffusion",
            state = RuntimeState.READY,
            description = "On-device Stable Diffusion image generation through the existing diffusion backend."
        ),
        RuntimeCapabilityInfo(
            capability = ModelCapability.OBJECT_DETECTION,
            runtime = "onnxruntime",
            state = RuntimeState.NEEDS_MODEL,
            description = "ONNX object detection adapter for mobile-friendly detection models."
        ),
        RuntimeCapabilityInfo(
            capability = ModelCapability.IMAGE_SEGMENTATION,
            runtime = "onnxruntime|gguf",
            state = RuntimeState.NEEDS_MODEL,
            description = "Segmentation adapter for compatible MobileSAM or ONNX segmentation models."
        ),
        RuntimeCapabilityInfo(
            capability = ModelCapability.VIDEO_GENERATION,
            runtime = "experimental",
            state = RuntimeState.EXPERIMENTAL,
            description = "Catalog and manifest support only until a stable Android video runtime is installed."
        ),
        RuntimeCapabilityInfo(
            capability = ModelCapability.THREE_D_GENERATION,
            runtime = "experimental",
            state = RuntimeState.EXPERIMENTAL,
            description = "Catalog and manifest support only until a stable Android 3D runtime is installed."
        )
    )

    fun statusJson(): String = json.encodeToString(RuntimeStatusResponse(capabilities = capabilities()))

    fun manifestSchemaJson(): String = """
        {
          "id": "vendor/model-name/runtime",
          "name": "Human readable model name",
          "source": { "type": "huggingface", "repo": "owner/repo" },
          "files": [
            { "path": "model.onnx", "sizeBytes": 123456, "sha256": null, "role": "model" }
          ],
          "capabilities": ["object_detection"],
          "runtime": "onnxruntime",
          "license": "apache-2.0",
          "minRamMb": 4096,
          "tags": ["mobile", "vision"],
          "experimental": false,
          "notes": "Optional compatibility notes"
        }
    """.trimIndent()

    fun importManifest(manifestJson: String): Result<ModelManifest> = runCatching {
        json.decodeFromString<ModelManifest>(manifestJson)
    }

    fun defaultCatalog(): List<ModelManifest> = listOf(
        ModelManifest(
            id = "gguf/custom-chat",
            name = "Custom GGUF Chat Model",
            source = ModelSource(type = "file"),
            files = listOf(ModelFile(path = "*.gguf")),
            capabilities = listOf(ModelCapability.CHAT),
            runtime = "gguf",
            tags = listOf("chat", "local", "gguf")
        ),
        ModelManifest(
            id = "onnx/ssd-mobilenet-v1-int8",
            name = "SSD MobileNet V1 INT8 Object Detection",
            source = ModelSource(type = "huggingface", repo = "onnxmodelzoo/ssd_mobilenet_v1_12-int8"),
            files = listOf(ModelFile(path = "ssd_mobilenet_v1_12-int8.onnx", role = "model")),
            capabilities = listOf(ModelCapability.OBJECT_DETECTION),
            runtime = "onnxruntime",
            license = "MIT",
            minRamMb = 2048,
            tags = listOf("vision", "object-detection", "mobile")
        ),
        ModelManifest(
            id = "gguf/mobilesam",
            name = "MobileSAM GGUF Segmentation",
            source = ModelSource(type = "huggingface", repo = "Acly/MobileSAM-GGUF"),
            files = listOf(ModelFile(path = "MobileSAM-F16.gguf", role = "model")),
            capabilities = listOf(ModelCapability.IMAGE_SEGMENTATION),
            runtime = "gguf",
            license = "apache-2.0",
            minRamMb = 3072,
            tags = listOf("vision", "segmentation", "mobile")
        )
    )

    fun defaultCatalogJson(): String = json.encodeToString(defaultCatalog())
}
