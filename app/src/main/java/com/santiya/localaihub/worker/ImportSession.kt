package com.santiya.localaihub.worker

import java.io.File

enum class ImportSourceKind {
    SAF_CONTENT_URI,
    DIRECT_FILE,
    SHARED_DOWNLOADS,
    APP_PRIVATE_FOLDER,
    DIRECT_URL,
    HUGGING_FACE_DOWNLOAD,
    GITHUB_RELEASE_ASSET,
}

enum class ImportReadMethod {
    METADATA_QUERY,
    OPEN_INPUT_STREAM,
    OPEN_FILE_DESCRIPTOR,
    OPEN_ASSET_FILE_DESCRIPTOR,
    DIRECT_DOWNLOADS_FILE,
    DIRECT_FILE_COPY,
}

enum class ImportCompatibility {
    RUNNABLE,
    IMPORTABLE_WITH_LIMITS,
    NEEDS_CONVERSION,
    UNSUPPORTED,
}

data class ImportReadAttempt(
    val method: ImportReadMethod,
    val success: Boolean,
    val detail: String,
)

data class ImportSessionResult(
    val sourceKind: ImportSourceKind,
    val readAttempts: List<ImportReadAttempt> = emptyList(),
    val usedStaging: Boolean = false,
    val stagedPath: String? = null,
    val sourceContentUri: String? = null,
    val finalInstalledPath: String? = null,
    val detectedFormat: String? = null,
    val compatibility: ImportCompatibility? = null,
    val conversionSuggestion: String? = null,
)

data class StagedImportSource(
    val sourceKind: ImportSourceKind,
    val displayName: String,
    val fileSize: Long,
    val stagedFile: File,
    val session: ImportSessionResult,
)

data class InstalledImportResult(
    val model: com.santiya.localaihub.models.table_schema.Model,
    val session: ImportSessionResult,
)
