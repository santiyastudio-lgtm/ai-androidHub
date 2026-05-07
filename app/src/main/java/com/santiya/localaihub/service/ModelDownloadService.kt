package com.santiya.localaihub.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.santiya.localaihub.data.AppSettingsDataStore
import com.santiya.localaihub.di.AppContainer
import com.santiya.localaihub.global.AccelerationMode
import com.santiya.localaihub.global.AppPaths
import com.santiya.localaihub.global.DeviceTuner
import com.santiya.localaihub.global.HardwareScanner
import com.santiya.localaihub.models.engine_schema.GgufEngineSchema
import com.santiya.localaihub.models.enums.PathType
import com.santiya.localaihub.models.enums.ProviderType
import com.santiya.localaihub.models.table_schema.Model
import com.santiya.localaihub.models.table_schema.ModelConfig
import com.santiya.localaihub.repo.ModelStoreRepository
import com.santiya.localaihub.storage.SharedModelLibrary
import com.santiya.localaihub.worker.DiffusionBackendSelector
import com.santiya.localaihub.worker.DiffusionConfig
import com.santiya.localaihub.worker.DiffusionInferenceParams
import com.santiya.localaihub.worker.GgufRuntimeSupport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class ModelDownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val notificationIdCounter = java.util.concurrent.atomic.AtomicInteger(NOTIFICATION_ID)

    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).build()

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "model_download_channel"
        private const val NOTIFICATION_ID = 3001

        private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
        val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates

        const val ACTION_START_DOWNLOAD = "action_start_download"
        const val ACTION_CANCEL_DOWNLOAD = "action_cancel_download"

        const val EXTRA_MODEL_ID = "model_id"
        const val EXTRA_MODEL_NAME = "model_name"
        const val EXTRA_FILE_URL = "file_url"
        const val EXTRA_IS_ZIP = "is_zip"
        const val EXTRA_MODEL_TYPE = "model_type"
        const val EXTRA_RUN_ON_CPU = "run_on_cpu"
        const val EXTRA_TEXT_EMBEDDING_SIZE = "text_embedding_size"
    }

    sealed class DownloadState {
        data class Downloading(
            val modelId: String,
            val progress: Float,
            val downloadedBytes: Long,
            val totalBytes: Long,
            val speedBytesPerSec: Long = 0,
            val etaSeconds: Long = -1
        ) : DownloadState()

        data class Extracting(
            val modelId: String,
            val currentFile: String = "",
            val extractedCount: Int = 0,
            val totalFiles: Int = 0
        ) : DownloadState()
        data class Processing(val modelId: String) : DownloadState()
        data class Success(val modelId: String) : DownloadState()
        data class Error(val modelId: String, val message: String) : DownloadState()
        data class Cancelled(val modelId: String) : DownloadState()
    }

    private fun updateDownloadState(modelId: String, state: DownloadState?) {
        _downloadStates.value = if (state == null) {
            _downloadStates.value - modelId
        } else {
            _downloadStates.value + (modelId to state)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: return START_NOT_STICKY
                val modelName = intent.getStringExtra(EXTRA_MODEL_NAME) ?: modelId
                val fileUrl = intent.getStringExtra(EXTRA_FILE_URL) ?: return START_NOT_STICKY
                val isZip = intent.getBooleanExtra(EXTRA_IS_ZIP, false)
                val modelType = intent.getStringExtra(EXTRA_MODEL_TYPE) ?: "GGUF"
                val runOnCpu = intent.getBooleanExtra(EXTRA_RUN_ON_CPU, false)
                val textEmbeddingSize = intent.getIntExtra(EXTRA_TEXT_EMBEDDING_SIZE, 768)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceCompat.startForeground(
                        this@ModelDownloadService, NOTIFICATION_ID,
                        createNotification(modelName, 0f, statusText = "0%"),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, createNotification(modelName, 0f, statusText = "0%"))
                }
                startDownload(
                    modelId,
                    modelName,
                    fileUrl,
                    isZip,
                    modelType,
                    runOnCpu,
                    textEmbeddingSize
                )
            }

            ACTION_CANCEL_DOWNLOAD -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID)
                if (modelId != null) {
                    cancelDownload(modelId)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(
        modelId: String,
        modelName: String,
        fileUrl: String,
        isZip: Boolean,
        modelType: String,
        runOnCpu: Boolean,
        textEmbeddingSize: Int
    ) {
        // Skip if this model is already downloading
        if (downloadJobs[modelId]?.isActive == true) {
            Log.w("DownloadService", "Download already in progress for $modelId, skipping duplicate")
            return
        }
        downloadJobs[modelId]?.cancel()
        cleanupStaleDownloadArtifacts(modelId)

        val notificationId = notificationIdCounter.incrementAndGet()
        val job = serviceScope.launch {
            var tempFile: File? = null
            var extractTempDir: File? = null
            try {
                updateDownloadState(modelId, DownloadState.Downloading(modelId, 0f, 0, 0))

                val tempDir = AppPaths.tempDownloads(applicationContext, modelId)
                tempDir.mkdirs()

                // Legacy Supertonic TTS downloads files directly, other runtimes use resolved assets
                if (modelType != "TTS") {
                    tempFile = File(tempDir, buildPartialFileName(modelId, fileUrl))
                    downloadFile(fileUrl, tempFile, modelId, modelName, notificationId)
                }

                when (modelType) {
                    "SD" -> {
                        val modelsDir = AppPaths.models(applicationContext)
                        modelsDir.mkdirs()

                        val modelDir = AppPaths.modelDir(applicationContext, modelId)

                        if (isZip) {
                            if (modelDir.exists()) {
                                modelDir.deleteRecursively()
                            }
                            modelDir.mkdirs()

                            extractTempDir = File(tempDir, "${modelId}_extract")
                            if (extractTempDir.exists()) {
                                extractTempDir.deleteRecursively()
                            }
                            extractTempDir.mkdirs()

                            updateDownloadState(modelId, DownloadState.Extracting(modelId))
                            updateNotification(modelName, 0f, notificationId, isExtracting = true)

                            unzipFile(tempFile!!, extractTempDir, modelId)

                            extractTempDir.listFiles()?.forEach { file ->
                                file.copyRecursively(File(modelDir, file.name), overwrite = true)
                            }
                            extractTempDir.deleteRecursively()
                            extractTempDir = null
                        } else {
                            if (!modelDir.exists()) {
                                modelDir.mkdirs()
                            }
                            tempFile?.copyTo(File(modelDir, tempFile.name), overwrite = true)
                        }

                        updateDownloadState(modelId, DownloadState.Processing(modelId))
                        updateNotification(modelName, 0f, notificationId, isProcessing = true)

                        insertModelToDatabase(
                            model = Model(
                                id = modelId,
                                modelName = modelName,
                                modelPath = modelDir.absolutePath,
                                pathType = PathType.DIRECTORY,
                                providerType = ProviderType.DIFFUSION,
                                fileSize = modelDir.walkTopDown().sumOf { it.length() },
                                isActive = true
                            ),
                            modelType = modelType,
                            runOnCpu = runOnCpu,
                            textEmbeddingSize = textEmbeddingSize
                        )
                    }

                    "GGUF" -> {
                        val installedModel = SharedModelLibrary.installManagedGgufFromFile(
                            context = applicationContext,
                            sourceFile = tempFile ?: error("Missing GGUF temp file"),
                            model = Model(
                                id = modelId,
                                modelName = modelName,
                                modelPath = "",
                                pathType = PathType.FILE,
                                providerType = ProviderType.GGUF,
                                fileSize = tempFile?.length(),
                                isActive = true
                            )
                        )

                        updateDownloadState(modelId, DownloadState.Processing(modelId))
                        updateNotification(modelName, 0f, notificationId, isProcessing = true)

                        insertModelToDatabase(
                            model = installedModel,
                            modelType = modelType,
                            runOnCpu = false,
                            textEmbeddingSize = 0
                        )
                    }

                    "TTS" -> {
                        AppPaths.models(applicationContext).mkdirs()

                        val ttsModelDir = AppPaths.ttsModel(applicationContext)
                        if (ttsModelDir.exists()) ttsModelDir.deleteRecursively()
                        ttsModelDir.mkdirs()

                        updateDownloadState(modelId, DownloadState.Processing(modelId))
                        updateNotification(modelName, 0f, notificationId, isProcessing = true)

                        // Download all TTS model files
                        downloadTTSModelFiles(ttsModelDir, modelId, modelName, notificationId)

                        insertModelToDatabase(
                            model = Model(
                                id = modelId,
                                modelName = modelName,
                                modelPath = ttsModelDir.absolutePath,
                                pathType = PathType.DIRECTORY,
                                providerType = ProviderType.TTS,
                                fileSize = ttsModelDir.walkTopDown().sumOf { it.length() },
                                isActive = true
                            ),
                            modelType = modelType,
                            runOnCpu = true,
                            textEmbeddingSize = 0
                        )
                    }

                    "TTS_PIPER", "ONNX", "RAW_ASSET", "IMAGE_TOOL" -> {
                        val targetDir = when (modelType) {
                            "TTS_PIPER" -> AppPaths.ttsVoicePack(applicationContext, modelId)
                            "ONNX" -> AppPaths.modelDir(applicationContext, modelId)
                            else -> AppPaths.rawAssetDir(applicationContext, modelId)
                        }
                        if (targetDir.exists()) {
                            targetDir.deleteRecursively()
                        }
                        targetDir.mkdirs()

                        if (isZip) {
                            extractTempDir = File(tempDir, "${modelId}_extract")
                            if (extractTempDir.exists()) {
                                extractTempDir.deleteRecursively()
                            }
                            extractTempDir.mkdirs()

                            updateDownloadState(modelId, DownloadState.Extracting(modelId))
                            updateNotification(modelName, 0f, notificationId, isExtracting = true)

                            unzipFile(tempFile!!, extractTempDir, modelId)

                            extractTempDir.listFiles()?.forEach { file ->
                                file.copyRecursively(File(targetDir, file.name), overwrite = true)
                            }
                            extractTempDir.deleteRecursively()
                            extractTempDir = null
                        } else {
                            val targetFile = File(
                                targetDir,
                                fileUrl.substringAfterLast('/').substringBefore('?').ifBlank { modelId }
                            )
                            tempFile?.copyTo(targetFile, overwrite = true)
                            if (modelType == "TTS_PIPER" && targetFile.extension.equals("onnx", ignoreCase = true)) {
                                val configUrl = "$fileUrl.json"
                                val configTarget = File(targetDir, "${targetFile.name}.json")
                                downloadAuxiliaryFile(configUrl, configTarget)
                            }
                        }

                        updateDownloadState(modelId, DownloadState.Processing(modelId))
                        updateNotification(modelName, 0f, notificationId, isProcessing = true)

                        val storedPath = when {
                            isZip -> targetDir.absolutePath
                            modelType == "ONNX" -> File(
                                targetDir,
                                fileUrl.substringAfterLast('/').substringBefore('?').ifBlank { modelId }
                            ).absolutePath
                            else -> targetDir.absolutePath
                        }
                        val storedFile = File(storedPath)

                        insertModelToDatabase(
                            model = Model(
                                id = modelId,
                                modelName = modelName,
                                modelPath = storedPath,
                                pathType = if (storedFile.isDirectory) PathType.DIRECTORY else PathType.FILE,
                                providerType = when (modelType) {
                                    "TTS_PIPER" -> ProviderType.TTS_PIPER
                                    "ONNX" -> ProviderType.ONNX
                                    else -> ProviderType.RAW_ASSET
                                },
                                fileSize = when {
                                    storedFile.isDirectory -> storedFile.walkTopDown().sumOf { it.length() }
                                    storedFile.exists() -> storedFile.length()
                                    else -> 0L
                                },
                                isActive = true
                            ),
                            modelType = if (modelType == "IMAGE_TOOL") "RAW_ASSET" else modelType,
                            runOnCpu = true,
                            textEmbeddingSize = 0
                        )

                        // No database entry вЂ” image tool models are managed by ImageToolsViewModel
                    }
                }

                tempFile?.delete()
                tempFile = null
                tempDir.deleteRecursively()

                updateDownloadState(modelId, DownloadState.Success(modelId))
                updateNotification(modelName, 100f, notificationId, isSuccess = true)

                withContext(Dispatchers.Main) {
                    kotlinx.coroutines.delay(2000)
                    updateDownloadState(modelId, null)
                    downloadJobs.remove(modelId)

                    if (downloadJobs.isEmpty()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }

            } catch (e: kotlinx.coroutines.CancellationException) {
                extractTempDir?.deleteRecursively()

                updateDownloadState(modelId, DownloadState.Cancelled(modelId))
                updateNotification(modelName, 0f, notificationId, isCancelled = true)

                withContext(Dispatchers.Main) {
                    kotlinx.coroutines.delay(2000)
                    updateDownloadState(modelId, null)
                    downloadJobs.remove(modelId)

                    if (downloadJobs.isEmpty()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            } catch (e: Exception) {
                extractTempDir?.deleteRecursively()
                if ((e.message ?: "").contains("ENOSPC", ignoreCase = true)) {
                    cleanupStaleDownloadArtifacts(modelId)
                }

                updateDownloadState(modelId, DownloadState.Error(modelId, e.message ?: "Unknown error"))
                updateNotification(modelName, 0f, notificationId, error = e.message)

                withContext(Dispatchers.Main) {
                    kotlinx.coroutines.delay(3000)
                    updateDownloadState(modelId, null)
                    downloadJobs.remove(modelId)

                    if (downloadJobs.isEmpty()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }

        downloadJobs[modelId] = job
    }

    private suspend fun downloadFile(
        url: String, destFile: File, modelId: String, modelName: String, notificationId: Int
    ) = withContext(Dispatchers.IO) {
        downloadBinaryFile(url = url, destination = destFile, shouldCancel = {
            !downloadJobs.containsKey(modelId) || downloadJobs[modelId]?.isCancelled == true
        }) { downloadedBytes, totalBytes, avgSpeed, eta ->
            val progress = if (totalBytes > 0L) {
                downloadedBytes.toFloat() / totalBytes
            } else 0f

            updateDownloadState(modelId, DownloadState.Downloading(
                modelId, progress, downloadedBytes, totalBytes, avgSpeed, eta
            ))

            val speedText = if (avgSpeed > 0L) {
                val etaText = if (eta >= 0L) " • ETA ${formatEtaCompact(eta)}" else ""
                "${formatSpeedCompact(avgSpeed)}$etaText"
            } else {
                "${(progress * 100).toInt()}%"
            }
            updateNotification(modelName, progress, notificationId, statusText = speedText)
        }
    }

    private suspend fun downloadBinaryFile(
        url: String,
        destination: File,
        shouldCancel: (() -> Boolean)? = null,
        onProgress: ((downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long, etaSeconds: Long) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()

        var existingBytes = destination.takeIf { it.exists() }?.length() ?: 0L
        val requestBuilder = Request.Builder().url(url)
        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        val call = client.newCall(requestBuilder.build())
        try {
            call.execute().use { response ->
                if (response.code == 416 && existingBytes > 0L) {
                    destination.delete()
                    throw Exception("Partial download is invalid. Restart the download.")
                }
                if (!response.isSuccessful) {
                    throw Exception("Download failed with code: ${response.code}")
                }

                val append = existingBytes > 0L && response.code == 206
                if (!append && existingBytes > 0L) {
                    destination.delete()
                    existingBytes = 0L
                }

                val body = response.body ?: throw Exception("Download response is empty")
                val totalBytes = resolveTotalBytes(response, existingBytes, body.contentLength(), append)
                var downloadedBytes = existingBytes
                var lastUpdateTime = 0L
                val speedSamples = mutableListOf<Long>()
                var lastSpeedBytes = downloadedBytes
                var lastSpeedTime = System.currentTimeMillis()

                if (downloadedBytes > 0L) {
                    onProgress?.invoke(downloadedBytes, totalBytes, 0L, -1L)
                }

                FileOutputStream(destination, append).buffered().use { output ->
                    body.byteStream().buffered().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var bytes: Int

                        while (input.read(buffer).also { bytes = it } != -1) {
                            if (shouldCancel?.invoke() == true) {
                                call.cancel()
                                throw kotlinx.coroutines.CancellationException("Download cancelled")
                            }

                            output.write(buffer, 0, bytes)
                            downloadedBytes += bytes

                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastUpdateTime >= 500 || (totalBytes > 0L && downloadedBytes >= totalBytes)) {
                                val elapsed = currentTime - lastSpeedTime
                                if (elapsed > 0L) {
                                    val bytesInInterval = downloadedBytes - lastSpeedBytes
                                    val speedSample = bytesInInterval * 1000 / elapsed
                                    speedSamples.add(speedSample)
                                    if (speedSamples.size > 5) speedSamples.removeAt(0)
                                    lastSpeedBytes = downloadedBytes
                                    lastSpeedTime = currentTime
                                }

                                val avgSpeed = if (speedSamples.isNotEmpty()) {
                                    speedSamples.average().toLong()
                                } else 0L
                                val eta = if (avgSpeed > 0L && totalBytes > 0L) {
                                    (totalBytes - downloadedBytes) / avgSpeed
                                } else -1L

                                lastUpdateTime = currentTime
                                onProgress?.invoke(downloadedBytes, totalBytes, avgSpeed, eta)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            call.cancel()
            throw e
        }
    }

    private suspend fun downloadAuxiliaryFile(
        url: String,
        destination: File
    ) = withContext(Dispatchers.IO) {
        downloadBinaryFile(url = url, destination = destination)
    }

    private fun resolveTotalBytes(
        response: Response,
        existingBytes: Long,
        responseBytes: Long,
        append: Boolean
    ): Long {
        if (append) {
            response.header("Content-Range")
                ?.substringAfterLast('/')
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?.let { return it }
            if (responseBytes >= 0L) {
                return existingBytes + responseBytes
            }
        }
        return responseBytes
    }

    private fun buildPartialFileName(modelId: String, fileUrl: String): String {
        val fileName = fileUrl.substringAfterLast('/').substringBefore('?').ifBlank { modelId }
        val sanitized = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "${modelId}_${sanitized}.part"
    }

    private suspend fun downloadFileLegacy(
        url: String, destFile: File, modelId: String, modelName: String, notificationId: Int
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val call = client.newCall(request)

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Download failed with code: ${response.code}")
                }

                val body = response.body
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L
                var lastUpdateTime = 0L

                // Speed tracking: rolling window of last 5 samples
                val speedSamples = mutableListOf<Long>()
                var lastSpeedBytes = 0L
                var lastSpeedTime = System.currentTimeMillis()

                FileOutputStream(destFile).buffered().use { output ->
                    body.byteStream().buffered().use { input ->
                        val buffer = ByteArray(64 * 1024) // 64KB for better throughput
                        var bytes: Int

                        while (input.read(buffer).also { bytes = it } != -1) {
                            if (!downloadJobs.containsKey(modelId) || downloadJobs[modelId]?.isCancelled == true) {
                                call.cancel()
                                throw kotlinx.coroutines.CancellationException("Download cancelled")
                            }

                            output.write(buffer, 0, bytes)
                            downloadedBytes += bytes

                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastUpdateTime >= 500 || downloadedBytes == totalBytes) {
                                // Calculate speed
                                val elapsed = currentTime - lastSpeedTime
                                if (elapsed > 0) {
                                    val bytesInInterval = downloadedBytes - lastSpeedBytes
                                    val speedSample = bytesInInterval * 1000 / elapsed
                                    speedSamples.add(speedSample)
                                    if (speedSamples.size > 5) speedSamples.removeAt(0)
                                    lastSpeedBytes = downloadedBytes
                                    lastSpeedTime = currentTime
                                }

                                val avgSpeed = if (speedSamples.isNotEmpty()) {
                                    speedSamples.average().toLong()
                                } else 0L

                                val eta = if (avgSpeed > 0 && totalBytes > 0) {
                                    (totalBytes - downloadedBytes) / avgSpeed
                                } else -1L

                                lastUpdateTime = currentTime
                                val progress = if (totalBytes > 0) {
                                    downloadedBytes.toFloat() / totalBytes
                                } else 0f

                                updateDownloadState(modelId, DownloadState.Downloading(
                                    modelId, progress, downloadedBytes, totalBytes, avgSpeed, eta
                                ))

                                val speedText = if (avgSpeed > 0L) {
                                    val etaText = if (eta >= 0L) " • ETA ${formatEtaCompact(eta)}" else ""
                                    "${formatSpeedCompact(avgSpeed)}$etaText"
                                } else {
                                    "${(progress * 100).toInt()}%"
                                }
                                updateNotification(modelName, progress, notificationId, statusText = speedText)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            call.cancel()
            throw e
        }
    }

    private suspend fun unzipFile(zipFile: File, destDir: File, modelId: String) = withContext(Dispatchers.IO) {
        // First pass: count valid entries
        val totalFiles = ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var count = 0
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.substringAfterLast('/')
                    if (name.isNotEmpty() && !name.startsWith(".") && !entry.name.contains("__MACOSX")) {
                        count++
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            count
        }

        // Second pass: extract with per-file progress
        var extractedCount = 0
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry

            while (entry != null) {
                // Check for cancellation
                if (!downloadJobs.containsKey(modelId) || downloadJobs[modelId]?.isCancelled == true) {
                    throw kotlinx.coroutines.CancellationException("Extraction cancelled")
                }

                if (!entry.isDirectory) {
                    val fileName = entry.name.substringAfterLast('/')
                    if (fileName.isNotEmpty() && !fileName.startsWith(".") && !entry.name.contains("__MACOSX")) {
                        updateDownloadState(modelId, DownloadState.Extracting(
                            modelId = modelId,
                            currentFile = fileName,
                            extractedCount = extractedCount,
                            totalFiles = totalFiles
                        ))

                        val file = File(destDir, fileName)
                        require(file.canonicalPath.startsWith(destDir.canonicalPath + File.separator)) {
                            "Zip entry path traversal detected: ${entry.name}"
                        }
                        FileOutputStream(file).buffered().use { output ->
                            zis.copyTo(output)
                        }
                        extractedCount++
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private suspend fun downloadTTSModelFiles(
        ttsModelDir: File, modelId: String, modelName: String, notificationId: Int
    ) = withContext(Dispatchers.IO) {
        val baseUrl = "https://huggingface.co/Supertone/supertonic-2/resolve/main"

        val onnxDir = File(ttsModelDir, "onnx")
        onnxDir.mkdirs()
        val voiceDir = File(ttsModelDir, "voice_styles")
        voiceDir.mkdirs()

        val onnxFiles = listOf(
            "onnx/duration_predictor.onnx",
            "onnx/text_encoder.onnx",
            "onnx/vector_estimator.onnx",
            "onnx/vocoder.onnx",
            "onnx/tts.json",
            "onnx/unicode_indexer.json"
        )

        val voiceFiles = listOf(
            "voice_styles/F1.json", "voice_styles/F2.json", "voice_styles/F3.json",
            "voice_styles/F4.json", "voice_styles/F5.json",
            "voice_styles/M1.json", "voice_styles/M2.json", "voice_styles/M3.json",
            "voice_styles/M4.json", "voice_styles/M5.json"
        )

        val allFiles = onnxFiles + voiceFiles
        var filesDownloaded = 0

        for (filePath in allFiles) {
            if (!downloadJobs.containsKey(modelId) || downloadJobs[modelId]?.isCancelled == true) {
                throw kotlinx.coroutines.CancellationException("TTS download cancelled")
            }

            val url = "$baseUrl/$filePath"
            val destFile = File(ttsModelDir, filePath)
            destFile.parentFile?.mkdirs()

            downloadAuxiliaryFile(url, destFile)

            filesDownloaded++
            val progress = filesDownloaded.toFloat() / allFiles.size
            updateDownloadState(modelId, DownloadState.Downloading(
                modelId, progress, filesDownloaded.toLong(), allFiles.size.toLong()
            ))
            updateNotification(modelName, progress, notificationId, statusText = "${(progress * 100).toInt()}%")
        }
    }

    private suspend fun downloadAuxiliaryFileLegacy(
        url: String,
        destination: File
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download auxiliary file: ${response.code}")
            }
            response.body.byteStream().use { input ->
                FileOutputStream(destination).buffered().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private suspend fun insertModelToDatabase(
        model: Model,
        modelType: String,
        runOnCpu: Boolean,
        textEmbeddingSize: Int
    ) = withContext(Dispatchers.IO) {
        val repository = AppContainer.getModelRepository()
        val storeRepository = ModelStoreRepository(this@ModelDownloadService)

        // Use the store model ID as primary key so the UI can match
        // installed models against store listings. SHA256 is still computed
        // for integrity but not used as the DB key.
        val providerType = when (modelType) {
            "SD" -> ProviderType.DIFFUSION
            "GGUF" -> ProviderType.GGUF
            "TTS" -> ProviderType.TTS
            "TTS_PIPER" -> ProviderType.TTS_PIPER
            "ONNX" -> ProviderType.ONNX
            "RAW_ASSET", "IMAGE_TOOL" -> ProviderType.RAW_ASSET
            else -> ProviderType.GGUF
        }
        val normalizedModel = model.copy(providerType = providerType)
        val existingModel = repository.getModelById(normalizedModel.id)
        if (existingModel != null) repository.updateModel(normalizedModel) else repository.insertModel(normalizedModel)

        val config = when (providerType) {
            ProviderType.DIFFUSION -> {
                val accelerationMode = AppSettingsDataStore(this@ModelDownloadService)
                    .accelerationMode
                    .firstOrNull() ?: AccelerationMode.AUTO
                val backendSelection = DiffusionBackendSelector.resolve(
                    mode = accelerationMode,
                    isQualcommDevice = storeRepository.isQualcommDevice(),
                    modelDir = File(normalizedModel.modelPath)
                )
                val diffusionConfig = DiffusionConfig(
                    textEmbeddingSize = textEmbeddingSize,
                    runOnCpu = backendSelection.runOnCpu,
                    useCpuClip = backendSelection.useCpuClip,
                    isPony = false,
                    httpPort = 8081,
                    safetyMode = false,
                    width = 512,
                    height = 512
                )
                val inferenceParams = DiffusionInferenceParams()
                ModelConfig(
                    modelId = normalizedModel.id,
                    modelLoadingParams = diffusionConfig.toJson(),
                    modelInferenceParams = inferenceParams.toJson()
                )
            }

            ProviderType.GGUF -> {
                val appSettings = AppSettingsDataStore(this@ModelDownloadService)
                val tuningEnabled = appSettings.hardwareTuningEnabled.firstOrNull() ?: true
                val loadingParams = if (tuningEnabled) {
                    val perfMode = appSettings.performanceMode.firstOrNull() ?: com.santiya.localaihub.global.PerformanceMode.BALANCED
                    val modelSizeMB = ((normalizedModel.fileSize ?: 0L) / (1024 * 1024)).toInt()
                    val profile = HardwareScanner.scan(this@ModelDownloadService)
                    DeviceTuner.tune(profile, modelSizeMB, normalizedModel.modelName, perfMode)
                } else {
                    com.santiya.localaihub.models.engine_schema.GgufLoadingParams()
                }
                val ggufSchema = GgufEngineSchema(loadingParams = loadingParams)
                val compatibility = GgufRuntimeSupport.inspect(File(normalizedModel.modelPath))
                val warningJson = if (!compatibility.supported) {
                    """{"warning":"runtime_unsupported","gguf_architecture":"${compatibility.architecture ?: "unknown"}","message":"${(compatibility.message ?: "").replace("\"", "\\\"")}"}"""
                } else null
                ModelConfig(
                    modelId = normalizedModel.id,
                    modelLoadingParams = ggufSchema.toLoadingJson(),
                    modelInferenceParams = warningJson ?: ggufSchema.toInferenceJson()
                )
            }

            ProviderType.GOOGLE_LOCAL -> {
                ModelConfig(
                    modelId = normalizedModel.id,
                    modelLoadingParams = """{"type":"google_local","runtime":"aicore"}""",
                    modelInferenceParams = """{"type":"chat","runtime":"google_local"}"""
                )
            }

            ProviderType.TTS -> {
                ModelConfig(
                    modelId = normalizedModel.id,
                    modelLoadingParams = """{"type":"tts","useNNAPI":false}""",
                    modelInferenceParams = """{"voice":"F1","speed":1.05,"steps":2,"language":"en"}"""
                )
            }

            ProviderType.TTS_PIPER -> {
                ModelConfig(
                    modelId = normalizedModel.id,
                    modelLoadingParams = """{"type":"tts_piper","runtime":"piper","useNNAPI":false}""",
                    modelInferenceParams = """{"voice":"ru","speed":1.0,"steps":1,"language":"ru"}"""
                )
            }

            ProviderType.ONNX -> {
                ModelConfig(
                    modelId = normalizedModel.id,
                    modelLoadingParams = """{"type":"onnx","runtime":"vision"}""",
                    modelInferenceParams = """{"capability":"vision"}"""
                )
            }

            ProviderType.RAW_ASSET -> {
                ModelConfig(
                    modelId = normalizedModel.id,
                    modelLoadingParams = """{"type":"raw_asset","runtime":"none"}""",
                    modelInferenceParams = """{"warning":"high_chance_not_runnable"}"""
                )
            }
        }

        val existingConfig = repository.getConfigByModelId(normalizedModel.id)
        if (existingConfig != null) repository.updateConfig(config) else repository.insertConfig(config)
    }

    private fun cancelDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
    }

    private fun cleanupStaleDownloadArtifacts(activeModelId: String? = null) {
        runCatching {
            val tempRoot = File(applicationContext.filesDir, "temp_downloads")
            val activeIds = downloadJobs
                .filterValues { it.isActive }
                .keys
                .toMutableSet()
                .apply { activeModelId?.let(::add) }

            tempRoot.listFiles().orEmpty().forEach { dir ->
                if (!dir.isDirectory) return@forEach
                if (activeIds.contains(dir.name)) return@forEach
                dir.deleteRecursively()
            }
        }.onFailure {
            Log.w("DownloadService", "Failed to cleanup stale temp downloads: ${it.message}")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID, "Model Downloads", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress of model downloads"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(
        modelName: String,
        progress: Float,
        statusText: String? = null,
        isExtracting: Boolean = false,
        isProcessing: Boolean = false
    ): android.app.Notification {
        val title = when {
            isProcessing -> "Processing $modelName"
            isExtracting -> "Extracting $modelName"
            else -> "Downloading $modelName"
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).setContentTitle(title)
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, (progress * 100).toInt(), isExtracting || isProcessing)
            .setOngoing(true).build()
    }

    private fun updateNotification(
        modelName: String,
        progress: Float,
        notificationId: Int,
        isSuccess: Boolean = false,
        error: String? = null,
        isExtracting: Boolean = false,
        isProcessing: Boolean = false,
        isCancelled: Boolean = false,
        statusText: String? = null,
    ) {
        val notification = when {
            isSuccess -> {
                NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Download Complete").setContentText(modelName)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done).setOngoing(false)
                    .build()
            }

            isCancelled -> {
                NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Download Cancelled").setContentText(modelName)
                    .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel).setOngoing(false)
                    .build()
            }

            error != null -> {
                NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Download Failed").setContentText(error)
                    .setSmallIcon(android.R.drawable.stat_notify_error).setOngoing(false).build()
            }

            else -> {
                createNotification(modelName, progress, statusText, isExtracting, isProcessing)
            }
        }

        notificationManager.notify(notificationId, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun formatSpeedCompact(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0L) return "0 B/s"
        val units = listOf("B/s", "KB/s", "MB/s", "GB/s")
        var value = bytesPerSecond.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex++
        }
        val formatted = if (value >= 100) "%.0f".format(value) else "%.1f".format(value)
        return "$formatted ${units[unitIndex]}"
    }

    private fun formatEtaCompact(seconds: Long): String {
        val minutes = seconds / 60
        val remain = seconds % 60
        return if (minutes > 0) "${minutes}m ${remain}s" else "${remain}s"
    }
}
