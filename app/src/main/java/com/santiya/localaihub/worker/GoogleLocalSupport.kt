package com.santiya.localaihub.worker

import android.content.Context
import com.santiya.googlelocalruntime.GoogleLocalAvailabilityState
import com.santiya.googlelocalruntime.GoogleLocalModelDescriptor
import com.santiya.googlelocalruntime.GoogleLocalRuntimeManager
import com.santiya.googlelocalruntime.GoogleLocalRuntimeType
import com.santiya.localaihub.models.table_schema.Model

object GoogleLocalSupport {

    fun isGemmaFamily(model: Model): Boolean = isGemmaFamily(model.modelName)

    fun isGemmaFamily(modelName: String): Boolean =
        modelName.contains("gemma", ignoreCase = true)

    fun buildGemmaDescriptor(modelId: String, modelName: String): GoogleLocalModelDescriptor {
        return GoogleLocalModelDescriptor(
            modelId = modelId,
            modelName = modelName,
            runtimeType = GoogleLocalRuntimeType.AICORE,
            maxTokens = 2048,
            topK = 32,
            topP = 0.9f,
            temperature = 0.8f,
            aicorePreference = "fast",
            aicoreReleaseStage = "stable",
        )
    }

    suspend fun availabilityForGemma(
        context: Context,
        modelId: String,
        modelName: String,
    ): GoogleLocalAvailabilityState {
        if (!isGemmaFamily(modelName)) return GoogleLocalAvailabilityState.UNAVAILABLE
        val manager = GoogleLocalRuntimeManager(context.applicationContext)
        return manager.getAicoreAvailability(buildGemmaDescriptor(modelId, modelName))
    }

    suspend fun shouldPreferForGemma(
        context: Context,
        modelId: String,
        modelName: String,
    ): Boolean {
        return availabilityForGemma(context, modelId, modelName) == GoogleLocalAvailabilityState.AVAILABLE
    }
}
