package com.santiya.localaihub.worker

import android.content.Context
import android.util.Log
import com.santiya.localaihub.data.AppSettingsDataStore
import com.santiya.localaihub.data.VaultManager
import com.santiya.localaihub.database.dao.RagDao
import com.santiya.localaihub.di.AppContainer
import com.santiya.localaihub.models.enums.PathType
import com.santiya.localaihub.models.engine_schema.GgufEngineSchema
import com.santiya.localaihub.models.table_schema.Model
import com.santiya.localaihub.models.table_schema.ModelConfig
import com.santiya.localaihub.repo.RagRepository
import com.santiya.localaihub.repo.ums.UmsMemoryRepository
import com.santiya.localaihub.repo.ums.UmsModelRepository
import com.santiya.localaihub.repo.ums.UmsPersonaRepository
import com.santiya.localaihub.storage.SharedModelLibrary
import com.santiya.localaihub.global.DeviceTuner
import com.santiya.localaihub.global.HardwareScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.santiya.localaihub.global.AppPaths
import java.io.File

/**
 * Runs once on app startup to detect and fix data integrity issues.
 * Non-destructive for valid data.
 */
class DataIntegrityManager(
    private val context: Context,
    private val modelRepo: UmsModelRepository,
    private val personaRepo: UmsPersonaRepository,
    private val ragDao: RagDao,
    private val memoryRepo: UmsMemoryRepository,
    private val ragRepository: RagRepository,
    private val appSettings: AppSettingsDataStore
) {

    companion object {
        private const val TAG = "DataIntegrityManager"
        private const val AI_MEMORY_CAP = 500
    }

    data class IntegrityReport(
        val danglingChatIdCleared: Boolean = false,
        val danglingPersonaIdCleared: Boolean = false,
        val danglingModelIdCleared: Boolean = false,
        val sharedModelsRecovered: Int = 0,
        val modelsDeactivated: Int = 0,
        val orphanedRagsDeleted: Int = 0,
        val orphanedAvatarsDeleted: Int = 0,
        val ragStateSynced: Boolean = false,
        val memoriesPruned: Int = 0
    ) {
        val totalFixes: Int
            get() = (if (danglingChatIdCleared) 1 else 0) +
                    (if (danglingPersonaIdCleared) 1 else 0) +
                    (if (danglingModelIdCleared) 1 else 0) +
                    sharedModelsRecovered +
                    modelsDeactivated +
                    orphanedRagsDeleted +
                    orphanedAvatarsDeleted +
                    (if (ragStateSynced) 1 else 0) +
                    memoriesPruned
    }

    suspend fun runFullCheck(): IntegrityReport = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting data integrity check...")
        val startTime = System.currentTimeMillis()

        var report = IntegrityReport()

        try {
            report = report.copy(danglingChatIdCleared = checkLastChatId())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking last chat ID", e)
        }

        try {
            report = report.copy(danglingPersonaIdCleared = checkActivePersonaId())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking active persona ID", e)
        }

        try {
            report = report.copy(danglingModelIdCleared = checkLastModelId())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking last model ID", e)
        }

        try {
            report = report.copy(sharedModelsRecovered = reconcileSharedGgufModels())
        } catch (e: Exception) {
            Log.e(TAG, "Error reconciling shared GGUF models", e)
        }

        try {
            report = report.copy(modelsDeactivated = checkModelFiles())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking model files", e)
        }

        try {
            report = report.copy(orphanedRagsDeleted = checkRagFiles())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking RAG files", e)
        }

        try {
            report = report.copy(orphanedAvatarsDeleted = checkOrphanedAvatars())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking orphaned avatars", e)
        }

        try {
            ragRepository.syncLoadedStateOnStartup()
            report = report.copy(ragStateSynced = true)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing RAG state", e)
        }

        try {
            report = report.copy(memoriesPruned = pruneExcessMemories())
        } catch (e: Exception) {
            Log.e(TAG, "Error pruning memories", e)
        }

        val duration = System.currentTimeMillis() - startTime
        Log.i(TAG, "Integrity check complete in ${duration}ms: ${report.totalFixes} fixes applied")
        if (report.totalFixes > 0) {
            Log.i(TAG, "Report: $report")
        }

        report
    }

    /**
     * LAST_CHAT_ID -> deleted chat: clear DataStore ref
     */
    private suspend fun checkLastChatId(): Boolean {
        val lastChatId = appSettings.lastChatId.first() ?: return false

        val chatRepo = VaultManager.chatRepo ?: return false
        val chats = chatRepo.getAllChats()
        val chatExists = chats.any { it.chatId == lastChatId }
        if (!chatExists) {
            Log.w(TAG, "LAST_CHAT_ID '$lastChatId' references deleted chat, clearing")
            appSettings.saveLastChatId(null)
            return true
        }
        return false
    }

    /**
     * ACTIVE_PERSONA_ID -> deleted persona: clear DataStore ref
     */
    private suspend fun checkActivePersonaId(): Boolean {
        val activeId = appSettings.activePersonaId.first() ?: return false

        val persona = personaRepo.getById(activeId)
        if (persona == null) {
            Log.w(TAG, "ACTIVE_PERSONA_ID '$activeId' references deleted persona, clearing")
            appSettings.saveActivePersonaId(null)
            return true
        }
        return false
    }

    /**
     * LAST_MODEL_ID -> deleted model: clear DataStore ref
     */
    private suspend fun checkLastModelId(): Boolean {
        val lastModelId = appSettings.lastModelId.first() ?: return false

        val model = modelRepo.getById(lastModelId)
        if (model == null) {
            Log.w(TAG, "LAST_MODEL_ID '$lastModelId' references deleted model, clearing")
            appSettings.saveLastModelId(null)
            return true
        }
        return false
    }

    /**
     * Model entry -> missing file on disk: deactivate (not delete).
     */
    private suspend fun checkModelFiles(): Int {
        val models = modelRepo.getAllOnce()
        var deactivated = 0

        for (model in models) {
            if (!model.isActive) continue
            if (!SharedModelLibrary.exists(context, model.modelPath, model.pathType)) {
                Log.w(TAG, "Model '${model.modelName}' file missing at ${model.modelPath}, deactivating")
                modelRepo.updateActiveStatus(model.id, false)
                deactivated++
            }
        }

        return deactivated
    }

    /**
     * RAG DB entry -> missing .neuron file: delete DB entry.
     */
    private suspend fun checkRagFiles(): Int {
        val rags = ragDao.getAllRagsOnce()
        var deleted = 0

        for (rag in rags) {
            val filePath = rag.filePath ?: continue
            val file = File(filePath)
            if (!file.exists()) {
                Log.w(TAG, "RAG '${rag.name}' file missing at $filePath, deleting DB entry")
                ragDao.deleteById(rag.id)
                deleted++
            }
        }

        return deleted
    }

    private suspend fun reconcileSharedGgufModels(): Int {
        val repository = AppContainer.getModelRepository()
        val sharedModels = SharedModelLibrary.scanManagedGgufModels(context)
        var recovered = 0

        for (shared in sharedModels) {
            val existing = repository.getModelById(shared.model.id)
            if (existing == null) {
                repository.insertModel(shared.model)
                repository.insertConfig(buildGgufConfig(shared.model))
                recovered++
                continue
            }

            val updated = existing.copy(
                modelName = shared.model.modelName,
                modelPath = shared.model.modelPath,
                pathType = shared.model.pathType,
                providerType = shared.model.providerType,
                fileSize = shared.model.fileSize,
                isActive = true
            )
            if (updated != existing) {
                repository.updateModel(updated)
                recovered++
            }

            if (repository.getConfigByModelId(shared.model.id) == null) {
                repository.insertConfig(buildGgufConfig(shared.model))
            }
        }

        return recovered
    }

    private suspend fun buildGgufConfig(model: Model): ModelConfig {
        val tuningEnabled = appSettings.hardwareTuningEnabled.first()
        val loadingParams = if (tuningEnabled) {
            val perfMode = appSettings.performanceMode.first()
            val modelSizeMB = ((model.fileSize ?: 0L) / (1024 * 1024)).toInt()
            val profile = HardwareScanner.scan(context)
            DeviceTuner.tune(profile, modelSizeMB, model.modelName, perfMode)
        } else {
            com.santiya.localaihub.models.engine_schema.GgufLoadingParams()
        }
        val schema = GgufEngineSchema(loadingParams = loadingParams)
        return ModelConfig(
            modelId = model.id,
            modelLoadingParams = schema.toLoadingJson(),
            modelInferenceParams = schema.toInferenceJson()
        )
    }

    /**
     * Avatar file on disk -> no Persona refs it: delete orphaned file.
     */
    private suspend fun checkOrphanedAvatars(): Int {
        val avatarDir = AppPaths.personaAvatars(context)
        if (!avatarDir.exists() || !avatarDir.isDirectory) return 0

        val personas = personaRepo.getAllOnce()
        val referencedPaths = personas.mapNotNull { it.avatarUri }.toSet()

        var deleted = 0
        avatarDir.listFiles()?.forEach { file ->
            if (file.isFile && file.absolutePath !in referencedPaths) {
                Log.w(TAG, "Orphaned avatar file: ${file.name}, deleting")
                file.delete()
                deleted++
            }
        }

        return deleted
    }

    /**
     * AI memories > 500: prune oldest beyond cap.
     */
    private suspend fun pruneExcessMemories(): Int {
        val count = memoryRepo.count()
        if (count <= AI_MEMORY_CAP) return 0

        val allMemories = memoryRepo.getAllOnce()
        val toDelete = allMemories.drop(AI_MEMORY_CAP)

        for (memory in toDelete) {
            memoryRepo.delete(memory)
        }

        Log.w(TAG, "Pruned ${toDelete.size} excess AI memories (had $count, cap $AI_MEMORY_CAP)")
        return toDelete.size
    }
}
