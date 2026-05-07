package com.santiya.localaihub.worker

import android.content.Context
import android.content.ContentUris
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

object ContentUriIO {
    private const val TAG = "ContentUriIO"

    data class ContentUriMetadata(
        val displayName: String,
        val fileSize: Long,
        val relativePath: String? = null,
    )

    fun openInputStream(context: Context, uri: Uri): InputStream {
        val resolver = context.contentResolver
        openResolverInputStream(resolver, uri)?.let { return it }
        openResolverFileDescriptor(resolver, uri)?.let { return ParcelFileDescriptor.AutoCloseInputStream(it) }
        openResolverAssetFileDescriptor(resolver, uri)?.let { return it.createInputStream() }
        resolveAlternateUri(context, uri)?.let { alternate ->
            Log.d(TAG, "Trying alternate URI $alternate for $uri")
            openResolverInputStream(resolver, alternate)?.let { return it }
            openResolverFileDescriptor(resolver, alternate)?.let { return ParcelFileDescriptor.AutoCloseInputStream(it) }
            openResolverAssetFileDescriptor(resolver, alternate)?.let { return it.createInputStream() }
        }
        resolveDirectFile(context, uri)?.takeIf { it.exists() && it.isFile }?.inputStream()?.let {
            Log.d(TAG, "Using direct file fallback ${it.javaClass.simpleName} for $uri")
            return it
        }
        throw FileNotFoundException("No item at $uri")
    }

    fun readMetadata(context: Context, uri: Uri): Pair<ContentUriMetadata, ImportReadAttempt> {
        val fallback = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported model"
        val result = runCatching {
            var displayName = fallback
            var fileSize = 0L
            var relativePath: String? = null
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val relativePathIndex = cursor.getColumnIndex("relative_path")
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) {
                        cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                    }
                    if (sizeIndex >= 0) {
                        fileSize = cursor.getLong(sizeIndex).takeIf { it > 0L } ?: 0L
                    }
                    if (relativePathIndex >= 0) {
                        relativePath = cursor.getString(relativePathIndex)?.takeIf { it.isNotBlank() }
                    }
                }
            }
            if (fileSize <= 0L) {
                fileSize = fileSize(context, uri)
            }
            ContentUriMetadata(displayName = displayName, fileSize = fileSize, relativePath = relativePath)
        }.recoverCatching {
            val alternate = resolveAlternateUri(context, uri) ?: throw it
            Log.d(TAG, "Metadata primary query failed for $uri, retrying with alternate URI $alternate")
            var displayName = fallback
            var fileSize = 0L
            var relativePath: String? = null
            context.contentResolver.query(alternate, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val relativePathIndex = cursor.getColumnIndex("relative_path")
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) {
                        cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                    }
                    if (sizeIndex >= 0) {
                        fileSize = cursor.getLong(sizeIndex).takeIf { value -> value > 0L } ?: 0L
                    }
                    if (relativePathIndex >= 0) {
                        relativePath = cursor.getString(relativePathIndex)?.takeIf { path -> path.isNotBlank() }
                    }
                }
            }
            if (fileSize <= 0L) {
                fileSize = fileSize(context, alternate)
            }
            ContentUriMetadata(displayName = displayName, fileSize = fileSize, relativePath = relativePath)
        }
        return if (result.isSuccess) {
            result.getOrThrow() to ImportReadAttempt(
                method = ImportReadMethod.METADATA_QUERY,
                success = true,
                detail = "Resolved metadata for $uri",
            )
        } else {
            ContentUriMetadata(displayName = fallback, fileSize = fileSize(context, uri)) to ImportReadAttempt(
                method = ImportReadMethod.METADATA_QUERY,
                success = false,
                detail = result.exceptionOrNull()?.message ?: "Metadata query failed",
            )
        }
    }

    fun stageToTempFile(
        context: Context,
        uri: Uri,
        preferredName: String? = null,
        sourceKind: ImportSourceKind = ImportSourceKind.SAF_CONTENT_URI,
    ): StagedImportSource {
        val attempts = mutableListOf<ImportReadAttempt>()
        val (metadata, metadataAttempt) = readMetadata(context, uri)
        attempts += metadataAttempt

        val safeName = sanitizeFileName(preferredName ?: metadata.displayName)
        val stageRoot = File(context.cacheDir, "import_staging").also { it.mkdirs() }
        val stageFile = File(stageRoot, "${System.currentTimeMillis()}-$safeName")

        val directOpen = tryCopyWithStrategy(context, uri, stageFile, ImportReadMethod.OPEN_INPUT_STREAM) {
            context.contentResolver.openInputStream(uri)
        }
        attempts += directOpen.second
        if (!directOpen.first) {
            val fdCopy = tryCopyWithStrategy(context, uri, stageFile, ImportReadMethod.OPEN_FILE_DESCRIPTOR) {
                context.contentResolver.openFileDescriptor(uri, "r")?.let { ParcelFileDescriptor.AutoCloseInputStream(it) }
            }
            attempts += fdCopy.second
            if (!fdCopy.first) {
                val assetCopy = tryCopyWithStrategy(context, uri, stageFile, ImportReadMethod.OPEN_ASSET_FILE_DESCRIPTOR) {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.createInputStream()
                }
                attempts += assetCopy.second
                if (!assetCopy.first) {
                    val alternateCopy = tryCopyWithStrategy(context, uri, stageFile, ImportReadMethod.OPEN_INPUT_STREAM) {
                        resolveAlternateUri(context, uri)?.let { alternate -> context.contentResolver.openInputStream(alternate) }
                    }
                    attempts += alternateCopy.second
                    if (!alternateCopy.first) {
                        val fileCopy = tryCopyWithStrategy(context, uri, stageFile, ImportReadMethod.DIRECT_DOWNLOADS_FILE) {
                            resolveDirectFile(context, uri)?.takeIf { it.exists() && it.isFile }?.inputStream()
                        }
                        attempts += fileCopy.second
                        if (!fileCopy.first) {
                            stageFile.delete()
                            throw FileNotFoundException("No item at $uri")
                        }
                    }
                }
            }
        }

        val actualSize = stageFile.length().takeIf { it > 0L } ?: metadata.fileSize
        return StagedImportSource(
            sourceKind = sourceKind,
            displayName = safeName,
            fileSize = actualSize,
            stagedFile = stageFile,
            session = ImportSessionResult(
                sourceKind = sourceKind,
                readAttempts = attempts,
                usedStaging = true,
                stagedPath = stageFile.absolutePath,
                sourceContentUri = uri.toString(),
            )
        )
    }

    fun fileSize(context: Context, uri: Uri): Long {
        val resolver = context.contentResolver
        openResolverFileDescriptor(resolver, uri)?.use { descriptor ->
            if (descriptor.statSize >= 0L) {
                return descriptor.statSize
            }
        }
        openResolverAssetFileDescriptor(resolver, uri)?.use { descriptor ->
            if (descriptor.length >= 0L) {
                return descriptor.length
            }
        }
        resolveAlternateUri(context, uri)?.let { alternate ->
            openResolverFileDescriptor(resolver, alternate)?.use { descriptor ->
                if (descriptor.statSize >= 0L) {
                    return descriptor.statSize
                }
            }
            openResolverAssetFileDescriptor(resolver, alternate)?.use { descriptor ->
                if (descriptor.length >= 0L) {
                    return descriptor.length
                }
            }
        }
        return 0L
    }

    fun displayName(context: Context, uri: Uri): String {
        val fallback = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported model"
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    cursor.getString(nameIndex)
                } else {
                    fallback
                }
            } ?: fallback
        }.recoverCatching {
            val alternate = resolveAlternateUri(context, uri) ?: throw it
            Log.d(TAG, "Display-name query failed for $uri, retrying with alternate URI $alternate")
            context.contentResolver.query(alternate, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    cursor.getString(nameIndex)
                } else {
                    fallback
                }
            } ?: fallback
        }.getOrDefault(fallback)
    }

    private fun tryCopyWithStrategy(
        context: Context,
        uri: Uri,
        target: File,
        method: ImportReadMethod,
        open: () -> InputStream?,
    ): Pair<Boolean, ImportReadAttempt> {
        return try {
            val stream = open()
            if (stream == null) {
                false to ImportReadAttempt(method, false, "Resolver returned null for $uri")
            } else {
                stream.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                true to ImportReadAttempt(method, true, "Copied $uri into staged storage")
            }
        } catch (error: Exception) {
            false to ImportReadAttempt(method, false, error.message ?: "$method failed")
        }
    }

    private fun resolveDirectFile(context: Context, uri: Uri): File? {
        val directDocumentPath = resolveDocumentFilePath(uri)
        if (directDocumentPath != null) {
            return File(directDocumentPath)
        }
        val metadata = runCatching { readMetadata(context, uri).first }.getOrNull()
        val relativePath = metadata?.relativePath?.trim()?.trimStart('/')
        val displayName = metadata?.displayName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: return null
        if (relativePath.isNullOrBlank()) {
            return null
        }
        val externalRoot = Environment.getExternalStorageDirectory()
        return File(externalRoot, "$relativePath$displayName")
    }

    private fun resolveAlternateUri(context: Context, uri: Uri): Uri? {
        if (uri.authority != "com.android.providers.downloads.documents") {
            return null
        }
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        return when {
            documentId.startsWith("msf:") -> {
                val numericId = documentId.substringAfter(':').toLongOrNull() ?: return null
                ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, numericId)
            }

            documentId.startsWith("raw:") -> null

            documentId.toLongOrNull() != null -> {
                ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"),
                    documentId.toLong()
                )
            }

            else -> null
        }
    }

    private fun resolveDocumentFilePath(uri: Uri): String? {
        if (uri.authority != "com.android.providers.downloads.documents") {
            return null
        }
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        return when {
            documentId.startsWith("raw:") -> documentId.removePrefix("raw:")
            else -> null
        }
    }

    private fun sanitizeFileName(value: String): String {
        return value
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "imported-model.bin" }
    }

    private fun openResolverInputStream(
        resolver: android.content.ContentResolver,
        uri: Uri,
    ): InputStream? {
        return runCatching { resolver.openInputStream(uri) }
            .onFailure { Log.d(TAG, "openInputStream failed for $uri: ${it.message}") }
            .getOrNull()
    }

    private fun openResolverFileDescriptor(
        resolver: android.content.ContentResolver,
        uri: Uri,
    ): ParcelFileDescriptor? {
        return runCatching { resolver.openFileDescriptor(uri, "r") }
            .onFailure { Log.d(TAG, "openFileDescriptor failed for $uri: ${it.message}") }
            .getOrNull()
    }

    private fun openResolverAssetFileDescriptor(
        resolver: android.content.ContentResolver,
        uri: Uri,
    ): android.content.res.AssetFileDescriptor? {
        return runCatching { resolver.openAssetFileDescriptor(uri, "r") }
            .onFailure { Log.d(TAG, "openAssetFileDescriptor failed for $uri: ${it.message}") }
            .getOrNull()
    }
}
