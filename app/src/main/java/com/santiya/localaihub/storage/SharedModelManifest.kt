package com.santiya.localaihub.storage

import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
data class SharedModelManifest(
    val id: String,
    val modelName: String,
    val providerType: String,
    val fileDisplayName: String,
    val relativePath: String,
    val sourceContentUri: String? = null,
    val fileSize: Long? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val MANIFEST_SUFFIX = ".santiya.json"

        fun manifestFileNameFor(displayName: String): String = "$displayName$MANIFEST_SUFFIX"

        fun fallbackModelIdFor(displayName: String): String {
            val normalized = displayName
                .trim()
                .lowercase(Locale.US)
                .replace(Regex("[^a-z0-9._-]+"), "-")
                .trim('-')
            return "shared-$normalized"
        }
    }
}
