package com.santiya.localaihub.storage

import android.content.Context
import android.provider.MediaStore
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.santiya.localaihub.global.AppPaths
import com.santiya.localaihub.models.enums.PathType
import com.santiya.localaihub.models.enums.ProviderType
import com.santiya.localaihub.models.table_schema.Model
import com.santiya.localaihub.worker.ContentUriIO
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class SharedManagedModel(
    val manifest: SharedModelManifest,
    val model: Model,
    val manifestUri: Uri?
)

object SharedModelLibrary {
    private const val TAG = "SharedModelLibrary"
    private const val ROOT_DIRECTORY = "SantiyaLocalAiHub/models"
    private const val GGUF_DIRECTORY = "gguf"
    private const val BINARY_MIME_TYPE = "application/octet-stream"
    private const val JSON_MIME_TYPE = "application/json"
    private const val MIN_FREE_BYTES_FOR_SHARED_BACKUP = 512L * 1024L * 1024L
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun ggufRelativePath(): String =
        "${Environment.DIRECTORY_DOWNLOADS}/$ROOT_DIRECTORY/$GGUF_DIRECTORY/"

    fun sharedGgufRoot(): File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "$ROOT_DIRECTORY/$GGUF_DIRECTORY"
        )

    private fun internalManagedGgufRoot(context: Context): File =
        File(AppPaths.models(context), "managed_gguf").also { it.mkdirs() }

    fun isManagedSharedPath(modelPath: String): Boolean {
        val rootPath = sharedGgufRoot().absolutePath
        val normalized = runCatching { File(modelPath).absolutePath }.getOrDefault(modelPath)
        val internalMarker = "${File.separator}files${File.separator}models${File.separator}managed_gguf${File.separator}"
        return normalized.startsWith(rootPath) || normalized.contains(internalMarker)
    }

    suspend fun installManagedGgufFromUri(
        context: Context,
        sourceUri: Uri,
        model: Model,
    ): Model = withContext(Dispatchers.IO) {
        ContentUriIO.openInputStream(context, sourceUri).use { input ->
            storeManagedGguf(context, input, model, sourceUri.toString())
        }
    }

    suspend fun installManagedGgufFromFile(
        context: Context,
        sourceFile: File,
        model: Model,
        sourceContentUri: String? = null,
    ): Model = withContext(Dispatchers.IO) {
        storeManagedGgufFromFile(context, sourceFile, model, sourceContentUri)
    }

    private fun storeManagedGgufFromFile(
        context: Context,
        sourceFile: File,
        model: Model,
        sourceContentUri: String? = null,
    ): Model {
        val displayName = normalizeGgufFileName(
            candidate = model.modelName.substringAfterLast('/').ifBlank { sourceFile.name.ifBlank { "${model.id}.gguf" } },
            sourceFile = sourceFile,
            fallbackId = model.id
        )
        val root = internalManagedGgufRoot(context)
        val relativePath = ggufRelativePath()
        val payloadFile = File(root, displayName)

        if (payloadFile.exists()) {
            payloadFile.delete()
        }
        val sourceIsAppTemp = sourceFile.absolutePath.startsWith(
            AppPaths.tempDownloads(context, model.id).absolutePath
        ) || sourceFile.absolutePath.startsWith(internalManagedGgufRoot(context).absolutePath)

        val moved = runCatching { sourceFile.renameTo(payloadFile) }.getOrDefault(false)
        val fileSize = if (moved && payloadFile.exists()) {
            payloadFile.length()
        } else {
            sourceFile.inputStream().use { input ->
                payloadFile.outputStream().use { output -> input.copyTo(output) }
            }
            payloadFile.length()
        }
        if (sourceIsAppTemp && !moved && sourceFile.exists() && sourceFile.absolutePath != payloadFile.absolutePath) {
            sourceFile.delete()
        }

        val manifest = SharedModelManifest(
            id = model.id,
            modelName = model.modelName,
            providerType = ProviderType.GGUF.name,
            fileDisplayName = displayName,
            relativePath = relativePath,
            sourceContentUri = sourceContentUri,
            fileSize = fileSize
        )
        val manifestFile = File(root, SharedModelManifest.manifestFileNameFor(displayName))
        manifestFile.bufferedWriter().use { writer ->
            writer.write(json.encodeToString(SharedModelManifest.serializer(), manifest))
        }

        runCatching {
            val sharedRoot = sharedGgufRoot().also { it.mkdirs() }
            val sharedPayload = File(sharedRoot, displayName)
            val enoughSharedSpace = sharedRoot.usableSpace > (payloadFile.length() + MIN_FREE_BYTES_FOR_SHARED_BACKUP)
            if (enoughSharedSpace && payloadFile.absolutePath != sharedPayload.absolutePath) {
                payloadFile.copyTo(sharedPayload, overwrite = true)
            }
            File(sharedRoot, SharedModelManifest.manifestFileNameFor(displayName)).bufferedWriter().use { writer ->
                writer.write(json.encodeToString(SharedModelManifest.serializer(), manifest))
            }
        }

        return model.copy(
            modelPath = payloadFile.absolutePath,
            pathType = PathType.FILE,
            providerType = ProviderType.GGUF,
            fileSize = fileSize,
            modelName = model.modelName.removeSuffix(".gguf")
        )
    }

    suspend fun scanManagedGgufModels(context: Context): List<SharedManagedModel> =
        withContext(Dispatchers.IO) {
            val root = sharedGgufRoot().also { it.mkdirs() }
            val internalRoot = internalManagedGgufRoot(context)
            val files = root.listFiles().orEmpty().toList()
            val manifestsByName = files
                .filter { it.isFile && it.name.endsWith(SharedModelManifest.MANIFEST_SUFFIX, ignoreCase = true) }
                .associateBy { it.name }

            val sharedBacked = files
                .filter { entry ->
                    entry.isFile &&
                        !entry.name.endsWith(SharedModelManifest.MANIFEST_SUFFIX, ignoreCase = true) &&
                        (
                            entry.name.endsWith(".gguf", ignoreCase = true) ||
                                File(root, SharedModelManifest.manifestFileNameFor(normalizeGgufFileName(entry.name, entry, entry.name)))
                                    .exists()
                            )
                }
                .mapNotNull { entry ->
                    val normalizedEntryName = normalizeGgufFileName(entry.name, entry, entry.name)
                    val normalizedEntry = if (entry.name == normalizedEntryName) entry else File(root, normalizedEntryName).also { normalized ->
                        if (!normalized.exists()) {
                            entry.renameTo(normalized)
                        }
                    }
                    val effectiveEntry = if (normalizedEntry.exists()) normalizedEntry else entry
                    val manifestName = SharedModelManifest.manifestFileNameFor(effectiveEntry.name)
                    val manifestEntry = manifestsByName[manifestName]
                    val manifest = manifestEntry?.let { readManifest(it) }
                        ?: SharedModelManifest(
                            id = SharedModelManifest.fallbackModelIdFor(effectiveEntry.name),
                            modelName = effectiveEntry.name.substringBeforeLast('.'),
                            providerType = ProviderType.GGUF.name,
                            fileDisplayName = effectiveEntry.name,
                            relativePath = ggufRelativePath(),
                            fileSize = effectiveEntry.length()
                        )
                    val providerType = runCatching { ProviderType.valueOf(manifest.providerType) }
                        .getOrDefault(ProviderType.GGUF)
                    if (providerType != ProviderType.GGUF) {
                        null
                    } else {
                        val runtimeFile = File(internalRoot, effectiveEntry.name)
                        if (!runtimeFile.exists() || runtimeFile.length() != effectiveEntry.length()) {
                            restoreManagedPayloadToRuntime(
                                context = context,
                                runtimeFile = runtimeFile,
                                sharedCandidate = effectiveEntry,
                                sourceContentUri = manifest.sourceContentUri,
                                candidateNames = listOf(
                                    effectiveEntry.name,
                                    normalizeGgufFileName(effectiveEntry.name, effectiveEntry, manifest.id),
                                    manifest.fileDisplayName,
                                    manifest.modelName,
                                    manifest.modelName.removeSuffix(".gguf"),
                                    "${manifest.modelName.removeSuffix(".gguf")}.gguf",
                                )
                            )
                        }
                        SharedManagedModel(
                            manifest = manifest,
                            model = Model(
                                id = manifest.id,
                                modelName = manifest.modelName,
                                modelPath = runtimeFile.absolutePath,
                                pathType = PathType.FILE,
                                providerType = ProviderType.GGUF,
                                fileSize = runtimeFile.length().takeIf { it > 0L } ?: manifest.fileSize,
                                isActive = true
                            ),
                            manifestUri = null
                        )
                    }
                }

            val internalOnly = internalRoot.listFiles().orEmpty()
                .map { entry ->
                    if (entry.isFile && !entry.name.endsWith(".gguf", ignoreCase = true)) {
                        val renamed = File(entry.parentFile ?: internalRoot, "${entry.name}.gguf")
                        if (!renamed.exists()) {
                            entry.renameTo(renamed)
                        }
                    }
                    (entry.parentFile ?: internalRoot).listFiles().orEmpty().firstOrNull {
                        it.name == entry.name || it.name == "${entry.name}.gguf"
                    } ?: entry
                }
                .filter { it.isFile && it.name.endsWith(".gguf", ignoreCase = true) }
                .map { entry ->
                    SharedManagedModel(
                        manifest = SharedModelManifest(
                            id = SharedModelManifest.fallbackModelIdFor(entry.name),
                            modelName = entry.name.substringBeforeLast('.'),
                            providerType = ProviderType.GGUF.name,
                            fileDisplayName = entry.name,
                            relativePath = ggufRelativePath(),
                            fileSize = entry.length()
                        ),
                        model = Model(
                            id = SharedModelManifest.fallbackModelIdFor(entry.name),
                            modelName = entry.name.substringBeforeLast('.'),
                            modelPath = entry.absolutePath,
                            pathType = PathType.FILE,
                            providerType = ProviderType.GGUF,
                            fileSize = entry.length(),
                            isActive = true
                        ),
                        manifestUri = null
                    )
                }

            (sharedBacked + internalOnly).distinctBy { it.model.id }
        }

    fun exists(context: Context, modelPath: String, pathType: PathType): Boolean {
        return when (pathType) {
            PathType.CONTENT_URI -> runCatching {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(modelPath), "r")?.use { afd ->
                    afd.length != 0L || afd.fileDescriptor.valid()
                } ?: false
            }.getOrDefault(false)

            PathType.FILE, PathType.DIRECTORY -> File(modelPath).exists()
        }
    }

    fun resolveManagedRuntimeModel(context: Context, model: Model): Model? {
        if (model.providerType != ProviderType.GGUF || model.pathType != PathType.FILE) {
            return null
        }
        val directFile = File(model.modelPath)
        if (directFile.exists()) {
            return model
        }
        val healed = resolveManagedRuntimeFile(
            context = context,
            modelId = model.id,
            modelName = model.modelName,
            requestedPath = model.modelPath,
        ) ?: return null
        return model.copy(
            modelPath = healed.absolutePath,
            pathType = PathType.FILE,
            providerType = ProviderType.GGUF,
            fileSize = healed.length().takeIf { it > 0L } ?: model.fileSize,
            isActive = true,
        )
    }

    suspend fun deleteManagedModel(context: Context, model: Model): Boolean =
        withContext(Dispatchers.IO) {
            when (model.pathType) {
                PathType.FILE -> {
                    val payloadFile = File(model.modelPath)
                    if (!isManagedSharedPath(payloadFile.absolutePath)) {
                        return@withContext false
                    }
                    val runtimeManifest = File(
                        payloadFile.parentFile,
                        SharedModelManifest.manifestFileNameFor(payloadFile.name)
                    )
                    if (runtimeManifest.exists()) {
                        runtimeManifest.delete()
                    }
                    val deletedRuntime = if (payloadFile.exists()) payloadFile.delete() else false
                    val sharedPayload = File(sharedGgufRoot(), payloadFile.name)
                    val sharedManifest = File(
                        sharedGgufRoot(),
                        SharedModelManifest.manifestFileNameFor(payloadFile.name)
                    )
                    if (sharedManifest.exists()) {
                        sharedManifest.delete()
                    }
                    val deletedShared = if (sharedPayload.exists()) sharedPayload.delete() else false
                    deletedRuntime || deletedShared
                }

                PathType.CONTENT_URI -> {
                    val payloadUri = runCatching { Uri.parse(model.modelPath) }.getOrNull() ?: return@withContext false
                    val payloadName = runCatching { ContentUriIO.displayName(context, payloadUri) }.getOrNull()
                        ?: return@withContext false
                    val payloadFile = File(sharedGgufRoot(), payloadName)
                    val manifestFile = File(
                        payloadFile.parentFile,
                        SharedModelManifest.manifestFileNameFor(payloadFile.name)
                    )
                    if (manifestFile.exists()) {
                        manifestFile.delete()
                    }
                    if (payloadFile.exists()) {
                        payloadFile.delete()
                    } else {
                        context.contentResolver.delete(payloadUri, null, null) > 0
                    }
                }

                PathType.DIRECTORY -> false
            }
        }

    private fun storeManagedGguf(
        context: Context,
        input: InputStream,
        model: Model,
        sourceContentUri: String?,
    ): Model {
        val tempFile = File.createTempFile("gguf-import-", ".tmp", internalManagedGgufRoot(context))
        tempFile.outputStream().use { output -> input.copyTo(output) }
        val storedModel = storeManagedGgufFromFile(context, tempFile, model, sourceContentUri)
        if (sourceContentUri.isNullOrBlank()) {
            return storedModel
        }
        return storedModel
    }

    private fun readManifest(file: File): SharedModelManifest? {
        return runCatching {
            file.bufferedReader().use { reader ->
                json.decodeFromString(SharedModelManifest.serializer(), reader.readText())
            }
        }.getOrNull()
    }

    private fun resolveManagedRuntimeFile(
        context: Context,
        modelId: String,
        modelName: String,
        requestedPath: String,
    ): File? {
        val internalRoot = internalManagedGgufRoot(context)
        val sharedRoot = sharedGgufRoot().also { it.mkdirs() }
        val requestedName = File(requestedPath).name
        val normalizedModelName = modelName.removeSuffix(".gguf")
        val names = linkedSetOf<String>()
        fun addCandidate(name: String?) {
            val value = name?.trim().orEmpty()
            if (value.isBlank() || value.endsWith(SharedModelManifest.MANIFEST_SUFFIX, ignoreCase = true)) return
            names += value
            names += normalizeGgufFileName(value, File(value), modelId)
        }

        addCandidate(requestedName)
        addCandidate(modelName.substringAfterLast('/').ifBlank { null })
        addCandidate(normalizedModelName)

        val manifests = sharedRoot.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(SharedModelManifest.MANIFEST_SUFFIX, ignoreCase = true) }
            .mapNotNull(::readManifest)
            .filter {
                it.providerType == ProviderType.GGUF.name &&
                    (it.id == modelId || it.modelName == modelName || it.modelName == normalizedModelName)
            }
        manifests.forEach { manifest ->
                addCandidate(manifest.fileDisplayName)
                addCandidate(manifest.modelName)
            }

        for (candidate in names) {
            val internalCandidate = File(internalRoot, candidate)
            if (internalCandidate.exists()) {
                return internalCandidate
            }
            val sharedCandidate = File(sharedRoot, candidate)
            if (sharedCandidate.exists()) {
                val runtimeName = normalizeGgufFileName(candidate, sharedCandidate, modelId)
                val runtimeFile = File(internalRoot, runtimeName)
                return restoreManagedPayloadToRuntime(
                    context = context,
                    runtimeFile = runtimeFile,
                    sharedCandidate = sharedCandidate,
                    sourceContentUri = manifests.firstOrNull {
                        it.fileDisplayName.equals(runtimeName, ignoreCase = true) ||
                            it.fileDisplayName.equals(sharedCandidate.name, ignoreCase = true)
                    }?.sourceContentUri,
                    candidateNames = listOf(
                        sharedCandidate.name,
                        runtimeName,
                        requestedName,
                        modelName.substringAfterLast('/'),
                        normalizedModelName,
                        "$normalizedModelName.gguf",
                    )
                )
            }
        }

        return null
    }

    private fun normalizeGgufFileName(candidate: String, sourceFile: File, fallbackId: String): String {
        val base = candidate.ifBlank { sourceFile.name.ifBlank { "$fallbackId.gguf" } }
        return if (base.endsWith(".gguf", ignoreCase = true)) base else "$base.gguf"
    }

    private fun restoreManagedPayloadToRuntime(
        context: Context,
        runtimeFile: File,
        sharedCandidate: File,
        sourceContentUri: String?,
        candidateNames: List<String>,
    ): File? {
        Log.i(TAG, "restoreManagedPayloadToRuntime: shared=${sharedCandidate.absolutePath}, runtime=${runtimeFile.absolutePath}, sourceUri=$sourceContentUri, candidates=$candidateNames")
        val resolvedUri = sourceContentUri?.let { Uri.parse(it) } ?: findDownloadUri(context, candidateNames)
        Log.i(TAG, "restoreManagedPayloadToRuntime: resolvedUri=$resolvedUri")
        val restoredFromMediaStore = resolvedUri?.let { uri ->
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    runtimeFile.outputStream().use { output -> input.copyTo(output) }
                }
                runtimeFile.exists() && runtimeFile.length() > 0L
            }.onFailure {
                Log.w(TAG, "restoreManagedPayloadToRuntime: MediaStore restore failed for $uri: ${it.message}")
            }.getOrDefault(false)
        } ?: false
        if (restoredFromMediaStore) {
            Log.i(TAG, "restoreManagedPayloadToRuntime: restored via MediaStore to ${runtimeFile.absolutePath}")
            return runtimeFile
        }
        return runCatching {
            sharedCandidate.copyTo(runtimeFile, overwrite = true)
            runtimeFile
        }.onFailure {
            Log.w(TAG, "restoreManagedPayloadToRuntime: direct shared copy failed from ${sharedCandidate.absolutePath}: ${it.message}")
        }.getOrNull()
    }

    private fun findDownloadUri(context: Context, candidateNames: List<String>): Uri? {
        val normalizedNames = candidateNames
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.endsWith(SharedModelManifest.MANIFEST_SUFFIX, ignoreCase = true) }
            .distinct()
        if (normalizedNames.isEmpty()) return null
        fun queryMediaStore(baseUri: Uri, displayName: String): Uri? {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
            )
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            val args = arrayOf(displayName)
            var fallbackUri: Uri? = null
            context.contentResolver.query(
                baseUri,
                projection,
                selection,
                args,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val relativeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val uri = Uri.withAppendedPath(baseUri, id.toString())
                    val relativePath = cursor.getString(relativeIndex).orEmpty()
                    Log.i(TAG, "findDownloadUri: matched displayName=$displayName uri=$uri relativePath=$relativePath base=$baseUri")
                    if (relativePath.contains(ROOT_DIRECTORY, ignoreCase = true)) {
                        return uri
                    }
                    if (fallbackUri == null) {
                        fallbackUri = uri
                    }
                }
            }
            return fallbackUri
        }
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.RELATIVE_PATH,
        )
        normalizedNames.forEach { displayName ->
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            val args = arrayOf(displayName)
            var fallbackUri: Uri? = null
            Log.i(TAG, "findDownloadUri: query displayName=$displayName")
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                "${MediaStore.Downloads.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val relativeIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.RELATIVE_PATH)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                    val relativePath = cursor.getString(relativeIndex).orEmpty()
                    Log.i(TAG, "findDownloadUri: matched displayName=$displayName uri=$uri relativePath=$relativePath")
                    if (relativePath.contains(ROOT_DIRECTORY, ignoreCase = true)) {
                        return uri
                    }
                    if (fallbackUri == null) {
                        fallbackUri = uri
                    }
                }
            }
            if (fallbackUri != null) {
                return fallbackUri
            }
            queryMediaStore(MediaStore.Files.getContentUri("external"), displayName)?.let { return it }
        }
        return null
    }
}
