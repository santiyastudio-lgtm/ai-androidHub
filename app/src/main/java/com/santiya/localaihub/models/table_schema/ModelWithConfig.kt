package com.santiya.localaihub.models.table_schema

import com.santiya.localaihub.models.engine_schema.GgufEngineSchema

fun GgufEngineSchema.toModelConfig(modelId: String): ModelConfig {
    return ModelConfig(
        modelId = modelId,
        modelLoadingParams = toLoadingJson(),
        modelInferenceParams = toInferenceJson()
    )
}