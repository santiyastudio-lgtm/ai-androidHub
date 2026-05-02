package com.santiya.localaihub.models.data

import com.santiya.localaihub.hub.ModelSupportStatus
import com.santiya.localaihub.hub.Downloadability
import com.santiya.localaihub.hub.CatalogWarning
import kotlinx.serialization.Serializable

@Serializable
data class HuggingFaceModel(
    val id: String,
    val name: String,
    val description: String,
    val fileUri: String,
    val approximateSize: String,
    val modelType: ModelType,
    val isZip: Boolean,
    val chipsetSuffix: String? = null,
    val runOnCpu: Boolean = false,
    val textEmbeddingSize: Int = 768,
    val tags: List<String> = emptyList(),
    val requiresNPU: Boolean = false,
    val repositoryUrl: String = "",
    val displayNameRu: String? = null,
    val descriptionRu: String? = null,
    val thumbnailUrl: String? = null,
    val ramEstimateMb: Int? = null,
    val supportStatus: ModelSupportStatus? = null,
    val assistantEligible: Boolean? = null,
    val liveEligible: Boolean? = null,
    val experimental: Boolean = false,
    val source: String = "huggingface",
    val sourceLabel: String = "Hugging Face",
    val pageUrl: String? = null,
    val downloadUrlOverride: String? = null,
    val resolvedFileName: String? = null,
    val previewImages: List<String> = emptyList(),
    val capabilities: List<String> = emptyList(),
    val familyTags: List<String> = emptyList(),
    val rawAssetOnly: Boolean = false,
    val downloadability: Downloadability = Downloadability.UNRESOLVED,
    val warnings: List<CatalogWarning> = emptyList()
)
@Serializable
data class HFModelRepository(
    val id: String,
    val name: String,
    val repoPath: String,
    val modelType: ModelType,
    val isEnabled: Boolean = true,
    val category: ModelCategory = ModelCategory.GENERAL
)
