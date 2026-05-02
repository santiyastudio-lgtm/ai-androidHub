package com.santiya.localaihub.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.santiya.localaihub.models.data.HFModelRepository
import com.santiya.localaihub.models.data.ModelCategory
import com.santiya.localaihub.models.data.ModelType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.modelRepoDataStore: DataStore<Preferences> by preferencesDataStore(name = "model_repositories")

class ModelRepositoryDataStore(private val context: Context) {

    companion object {
        private val MODEL_REPOS_KEY = stringPreferencesKey("model_repositories")
        private val DELETED_DEFAULTS_KEY = stringPreferencesKey("deleted_default_repo_ids")

        val DEFAULT_REPOSITORIES = listOf(
            // === GENERAL ===
            HFModelRepository(
                id = "unsloth-qwen3_5-0_8b",
                name = "Qwen3.5 (0.8B)",
                repoPath = "unsloth/Qwen3.5-0.8B-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.GENERAL
            ),
            HFModelRepository(
                id = "unsloth-qwen3_5-4b",
                name = "Qwen3.5 (4B)",
                repoPath = "unsloth/Qwen3.5-4B-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.GENERAL
            ),
            HFModelRepository(
                id = "unsloth-qwen3_5-9b",
                name = "Qwen3.5 (9B)",
                repoPath = "unsloth/Qwen3.5-9B-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.GENERAL
            ),
            HFModelRepository(
                id = "liquidai-lfm2-350m",
                name = "LFM2 350M",
                repoPath = "LiquidAI/LFM2-350M-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.GENERAL
            ),
            // === UNCENSORED ===
            HFModelRepository(
                id = "qwen3_5-9b-uncensored-hauhau",
                name = "Qwen3.5 9B Uncensored",
                repoPath = "HauhauCS/Qwen3.5-9B-Uncensored-HauhauCS-Aggressive",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.UNCENSORED
            ),
            HFModelRepository(
                id = "qwen3_5-9b-claude-opus-uncensored",
                name = "Qwen3.5 9B Claude 4.6 Opus Uncensored",
                repoPath = "LuffyTheFox/Qwen3.5-9B-Claude-4.6-Opus-Distilled-Uncensored-v2-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.UNCENSORED
            ),
            HFModelRepository(
                id = "qwen3_5-9b-abliterated",
                name = "Qwen3.5 9B Abliterated",
                repoPath = "lukey03/Qwen3.5-9B-abliterated-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.UNCENSORED
            ),
            HFModelRepository(
                id = "dolphin-mistral-7b-uncensored",
                name = "Dolphin Mistral 7B Uncensored",
                repoPath = "QuantFactory/dolphin-2.6-mistral-7b-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.UNCENSORED
            ),
            // === IMAGE GENERATION (SD) ===
            HFModelRepository(
                id = "sd-qnn",
                name = "Stable Diffusion (NPU)",
                repoPath = "xororz/sd-qnn",
                modelType = ModelType.SD,
                isEnabled = true,
                category = ModelCategory.GENERAL
            ),
            HFModelRepository(
                id = "sd-mnn",
                name = "Stable Diffusion (CPU)",
                repoPath = "xororz/sd-mnn",
                modelType = ModelType.SD,
                isEnabled = true,
                category = ModelCategory.GENERAL
            ),
            // === NSFW IMAGE GENERATION (SD) ===
            HFModelRepository(
                id = "sd-mistoonanime-qnn",
                name = "MistoonAnime v3.0 (NPU)",
                repoPath = "Mr-J-369/mistoonAnime_v30-SD1.5-qnn2.28",
                modelType = ModelType.SD,
                isEnabled = true,
                category = ModelCategory.UNCENSORED
            ),
            HFModelRepository(
                id = "sd-cyberrealistic-qnn",
                name = "CyberRealistic Classic (NPU)",
                repoPath = "Mr-J-369/cyberrealistic-classic-SD1.5-qnn2.28",
                modelType = ModelType.SD,
                isEnabled = true,
                category = ModelCategory.UNCENSORED
            ),
            HFModelRepository(
                id = "sd-realhotspice-qnn",
                name = "RealHotSpice (NPU)",
                repoPath = "Mr-J-369/RealHotSpice-SD1.5-qnn2.28",
                modelType = ModelType.SD,
                isEnabled = true,
                category = ModelCategory.UNCENSORED
            )
        )
    }

    val repositories: Flow<List<HFModelRepository>> =
        context.modelRepoDataStore.data.map { preferences ->
            val json = preferences[MODEL_REPOS_KEY]
            val deletedJson = preferences[DELETED_DEFAULTS_KEY]
            val deletedIds = deletedJson?.let {
                try { Json.decodeFromString<Set<String>>(it) } catch (_: Exception) { emptySet() }
            } ?: emptySet()

            if (json != null) {
                try {
                    val saved = migrateLegacyRepositories(Json.decodeFromString<List<HFModelRepository>>(json))
                    val savedIds = saved.map { it.id }.toSet()
                    val newDefaults = DEFAULT_REPOSITORIES.filter {
                        it.id !in savedIds && it.id !in deletedIds
                    }
                    if (newDefaults.isNotEmpty()) saved + newDefaults else saved
                } catch (e: Exception) {
                    DEFAULT_REPOSITORIES
                }
            } else {
                DEFAULT_REPOSITORIES
            }
        }

    private fun migrateLegacyRepositories(saved: List<HFModelRepository>): List<HFModelRepository> {
        val replacements = mapOf(
            "Novaciano/Gemma3-Emophilic-1B-GGUF" to DEFAULT_REPOSITORIES.first { it.id == "qwen3_5-9b-uncensored-hauhau" },
            "mradermacher/Gemma3-Emotional-1B-i1-GGUF" to DEFAULT_REPOSITORIES.first { it.id == "qwen3_5-9b-claude-opus-uncensored" },
            "mradermacher/SEX_ROLEPLAY-3.2-1B-i1-GGUF" to DEFAULT_REPOSITORIES.first { it.id == "qwen3_5-9b-abliterated" }
        )
        return saved.map { repo ->
            replacements[repo.repoPath]?.copy(isEnabled = repo.isEnabled) ?: repo
        }.distinctBy { it.id }
    }

    suspend fun saveRepositories(repos: List<HFModelRepository>) {
        context.modelRepoDataStore.edit { preferences ->
            preferences[MODEL_REPOS_KEY] = Json.encodeToString(repos)
        }
    }

    suspend fun addRepository(repo: HFModelRepository) {
        val current = repositories.first()
        saveRepositories(current + repo)
    }

    suspend fun removeRepository(repoId: String) {
        val current = repositories.first()
        saveRepositories(current.filterNot { it.id == repoId })
        if (DEFAULT_REPOSITORIES.any { it.id == repoId }) {
            context.modelRepoDataStore.edit { preferences ->
                val existing = preferences[DELETED_DEFAULTS_KEY]?.let {
                    try { Json.decodeFromString<Set<String>>(it) } catch (_: Exception) { emptySet() }
                } ?: emptySet()
                preferences[DELETED_DEFAULTS_KEY] = Json.encodeToString(existing + repoId)
            }
        }
    }

    suspend fun toggleRepository(repoId: String) {
        val current = repositories.first()
        saveRepositories(current.map {
            if (it.id == repoId) it.copy(isEnabled = !it.isEnabled)
            else it
        })
    }

    suspend fun updateRepository(repo: HFModelRepository) {
        val current = repositories.first()
        saveRepositories(current.map {
            if (it.id == repo.id) repo else it
        })
    }
}
