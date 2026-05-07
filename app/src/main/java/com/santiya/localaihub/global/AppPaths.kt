package com.santiya.localaihub.global

import android.content.Context
import java.io.File

/**
 * Centralized path registry for all app directories.
 * All file I/O should reference paths from here вЂ” never hardcode directory names.
 */
object AppPaths {

    // в”Ђв”Ђ Core Directories в”Ђв”Ђ

    /** Unified Memory System storage */
    fun ums(context: Context): File =
        File(context.filesDir, "ums")

    /** Legacy encrypted vault (migration only) */
    fun memoryVault(context: Context): File =
        File(context.filesDir, "memory_vault")

    /** Legacy vault file (migration only) */
    fun vaultFile(context: Context): File =
        File(context.filesDir, "memory_vault/vault.mvlt")

    // в”Ђв”Ђ Model Directories в”Ђв”Ђ

    /** Root models directory */
    fun models(context: Context): File =
        File(context.filesDir, "models")

    /** Specific model directory (for diffusion) */
    fun modelDir(context: Context, modelId: String): File =
        File(models(context), modelId)

    /** Specific GGUF model file */
    fun modelFile(context: Context, modelId: String): File =
        File(models(context), "$modelId.gguf")

    /** TTS model directory */
    fun ttsModel(context: Context): File =
        File(models(context), "supertonic-2")

    /** Piper voice packs and other local voice bundles */
    fun ttsVoicePacks(context: Context): File =
        File(models(context), "tts_voice_packs").also { it.mkdirs() }

    /** Specific Piper or other voice pack directory */
    fun ttsVoicePack(context: Context, modelId: String): File =
        File(ttsVoicePacks(context), modelId).also { it.mkdirs() }

    /** Downloaded assets that may not be runnable locally */
    fun rawAssets(context: Context): File =
        File(models(context), "raw_assets").also { it.mkdirs() }

    /** Specific raw asset directory */
    fun rawAssetDir(context: Context, modelId: String): File =
        File(rawAssets(context), modelId).also { it.mkdirs() }

    /** Embedding model file */
    fun embeddingModel(context: Context): File =
        File(context.filesDir, "embedding_model/all-MiniLM-L6-v2-Q5_K_M.gguf")

    /** Temporary download directory for a model */
    fun tempDownloads(context: Context, modelId: String): File =
        File(context.filesDir, "temp_downloads/$modelId")

    /** Prompt KV cache directory */
    fun promptCache(context: Context): File =
        File(context.cacheDir, "prompt_cache")

    // в”Ђв”Ђ Data Directories в”Ђв”Ђ

    /** RAG databases */
    fun rags(context: Context): File =
        File(context.filesDir, "rags").also { it.mkdirs() }

    /** Specific RAG file */
    fun ragFile(context: Context, ragId: String): File =
        File(rags(context), "$ragId.neuron")

    /** Persona avatar images */
    fun personaAvatars(context: Context): File =
        File(context.filesDir, "persona_avatars")

    /** User-editable workspace shared with local file tools and file-aware AI flows. */
    fun workspaceFiles(context: Context): File =
        File(context.filesDir, "workspace_files").also { it.mkdirs() }

    fun offlineCityRoot(context: Context): File =
        File(context.filesDir, "offline_city").also { it.mkdirs() }

    fun offlineCityImports(context: Context): File =
        File(offlineCityRoot(context), "imports").also { it.mkdirs() }

    fun offlineCityDataset(context: Context): File =
        File(offlineCityRoot(context), "active_city_dataset.json")

    // в”Ђв”Ђ Image Tools в”Ђв”Ђ

    /** Image tool model weights (upscaler, segmenter, lama, depth, style) */
    fun imageTools(context: Context): File =
        File(context.filesDir, "image_tools")

    /** Specific image tool model file */
    fun imageToolModel(context: Context, fileName: String): File =
        File(imageTools(context), fileName)

    // в”Ђв”Ђ Agent Space в”Ђв”Ђ

    fun agentProjects(context: Context): File =
        File(context.filesDir, "agent_projects").also { it.mkdirs() }

    fun agentProjectDir(context: Context, projectId: String): File =
        File(agentProjects(context), projectId).also { it.mkdirs() }
}
