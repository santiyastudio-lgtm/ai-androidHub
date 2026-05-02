package com.santiya.localaihub.hub

import android.app.ActivityManager
import android.content.Context
import com.santiya.localaihub.models.enums.ProviderType
import com.santiya.localaihub.models.table_schema.Model
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ModelOrchestraManager(private val context: Context) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun getEligibleModels(models: List<Model>): List<Model> {
        return models.filter { model ->
            model.providerType == ProviderType.GGUF &&
                ((model.fileSize ?: Long.MAX_VALUE) / (1024 * 1024)) <= SMALL_MODEL_LIMIT_MB
        }
    }

    fun capabilityState(
        models: List<Model>,
        config: OrchestraConfig
    ): OrchestraCapabilityState {
        val eligible = getEligibleModels(models)
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(memoryInfo)
        val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
        val supported = totalRamMb >= 6144 && eligible.size >= 2
        val reason = when {
            !config.enabled -> "Оркестр выключен в настройках."
            !supported && eligible.size < 2 -> "Нужно минимум две маленькие GGUF-модели."
            !supported -> "Недостаточно памяти для локального оркестра."
            else -> "Оркестр может работать локально."
        }
        return OrchestraCapabilityState(
            mode = if (config.enabled) {
                OrchestraExecutionMode.SMALL_MODEL_ORCHESTRA
            } else {
                OrchestraExecutionMode.SINGLE_MODEL
            },
            supported = supported,
            eligibleModelIds = eligible.map { it.id },
            reason = reason
        )
    }

    fun capabilityStateJson(models: List<Model>, config: OrchestraConfig): String =
        json.encodeToString(capabilityState(models, config))

    companion object {
        const val SMALL_MODEL_LIMIT_MB = 1024L
    }
}
