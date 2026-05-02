package com.santiya.localaihub.catalog

import com.santiya.localaihub.hub.ModelSupportStatus

enum class CatalogSource(val wireName: String, val displayName: String) {
    HUGGING_FACE("huggingface", "Hugging Face"),
    CIVITAI("civitai", "Civitai"),
    MODELSCOPE("modelscope", "ModelScope"),
    GITHUB("github", "GitHub");

    companion object {
        fun fromWireName(value: String): CatalogSource? =
            entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) }
    }
}

data class CatalogSearchFilters(
    val sources: Set<CatalogSource> = CatalogSource.entries.toSet(),
    val capabilities: Set<String> = emptySet(),
    val familyTags: Set<String> = emptySet(),
    val supportStatuses: Set<ModelSupportStatus> = emptySet(),
    val executionTargets: Set<String> = emptySet(),
    val includeNsfw: Boolean = true,
)

interface CatalogSourceAdapter {
    val source: CatalogSource
    suspend fun search(query: String, filters: CatalogSearchFilters, limit: Int): List<com.santiya.localaihub.models.data.HuggingFaceModel>
}
