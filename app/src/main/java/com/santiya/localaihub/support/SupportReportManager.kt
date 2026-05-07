package com.santiya.localaihub.support

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.santiya.localaihub.global.AppLanguage
import com.santiya.localaihub.hub.LocalBackendOption
import com.santiya.localaihub.models.table_schema.Model
import com.santiya.localaihub.tts.VoiceRuntimeOption
import com.santiya.localaihub.ui.screen.memory.LogEntry
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val SUPPORT_PREFS = "santiya_support_prefs"
private const val SUPPORT_INSTALLATION_ID = "support_installation_id"
private const val SUPPORT_BOT_USERNAME = "SantiyaSupportBot"

data class SupportReportRequest(
    val userComment: String,
    val language: AppLanguage,
    val openClawBackend: LocalBackendOption,
    val voiceRuntime: VoiceRuntimeOption,
    val streamingEnabled: Boolean,
    val chatMemoryEnabled: Boolean,
    val performanceMode: String,
    val accelerationMode: String,
    val installedModels: List<Model>,
    val vaultLogs: List<LogEntry>,
)

data class SupportReportBundle(
    val ticketId: String,
    val archiveFile: File,
    val archiveSha256: String,
    val shareText: String,
    val telegramStartPayload: String,
    val telegramBotUsername: String = SUPPORT_BOT_USERNAME,
)

@Serializable
private data class SupportReportManifest(
    val schemaVersion: Int = 1,
    val ticketId: String,
    val supportBotUsername: String,
    val createdAtEpochMs: Long,
    val installationId: String,
    val app: SupportAppInfo,
    val device: SupportDeviceInfo,
    val settings: SupportSettingsSnapshot,
    val installedModels: List<SupportModelSnapshot>,
    val userComment: String,
    val commentPreview: String,
    val attachments: List<SupportAttachmentSnapshot>,
)

@Serializable
private data class SupportAppInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val firstInstallTimeEpochMs: Long,
    val lastUpdateTimeEpochMs: Long,
    val signerSha256: String?,
)

@Serializable
private data class SupportDeviceInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val sdkInt: Int,
    val release: String,
    val supportedAbis: List<String>,
)

@Serializable
private data class SupportSettingsSnapshot(
    val language: String,
    val openClawBackend: String,
    val voiceRuntime: String,
    val streamingEnabled: Boolean,
    val chatMemoryEnabled: Boolean,
    val performanceMode: String,
    val accelerationMode: String,
)

@Serializable
private data class SupportModelSnapshot(
    val id: String,
    val modelName: String,
    val providerType: String,
    val pathType: String,
    val fileSize: Long?,
    val isActive: Boolean,
    val pathHint: String,
)

@Serializable
private data class SupportAttachmentSnapshot(
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
)

class SupportReportManager(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
    }

    fun prepare(request: SupportReportRequest): SupportReportBundle {
        cleanOldReports()

        val timestamp = System.currentTimeMillis()
        val ticketId = buildTicketId(timestamp)
        val baseDir = File(context.cacheDir, "support-reports/$ticketId").apply { mkdirs() }
        val archiveFile = File(context.cacheDir, "support-reports/santiya-support-$ticketId.zip")
        val installationId = readOrCreateInstallationId()

        val commentText = request.userComment.trim()
        val commentFile = File(baseDir, "user_comment.txt")
        commentFile.writeText(
            if (commentText.isNotEmpty()) commentText else "No user comment provided.",
            Charsets.UTF_8
        )

        val appInfoText = buildAppDiagnosticsText(ticketId, installationId)
        val diagnosticsFile = File(baseDir, "diagnostics.txt")
        diagnosticsFile.writeText(appInfoText, Charsets.UTF_8)

        val internalLogText = formatVaultLogs(request.vaultLogs)
        val internalLogFile = File(baseDir, "internal_logs.txt")
        internalLogFile.writeText(internalLogText, Charsets.UTF_8)

        val logcatText = captureOwnProcessLogcat()
        val logcatFile = File(baseDir, "logcat.txt")
        logcatFile.writeText(logcatText, Charsets.UTF_8)

        val attachmentFiles = listOf(commentFile, diagnosticsFile, internalLogFile, logcatFile)
        val attachments = attachmentFiles.map { file ->
            SupportAttachmentSnapshot(
                fileName = file.name,
                sizeBytes = file.length(),
                sha256 = sha256(file)
            )
        }

        val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val manifest = SupportReportManifest(
            ticketId = ticketId,
            supportBotUsername = SUPPORT_BOT_USERNAME,
            createdAtEpochMs = timestamp,
            installationId = installationId,
            app = SupportAppInfo(
                packageName = context.packageName,
                versionName = packageInfo.versionName ?: "unknown",
                versionCode = packageInfo.longVersionCode,
                firstInstallTimeEpochMs = packageInfo.firstInstallTime,
                lastUpdateTimeEpochMs = packageInfo.lastUpdateTime,
                signerSha256 = signerSha256(packageInfo)
            ),
            device = SupportDeviceInfo(
                manufacturer = Build.MANUFACTURER,
                brand = Build.BRAND,
                model = Build.MODEL,
                device = Build.DEVICE,
                product = Build.PRODUCT,
                sdkInt = Build.VERSION.SDK_INT,
                release = Build.VERSION.RELEASE ?: "unknown",
                supportedAbis = Build.SUPPORTED_ABIS.toList()
            ),
            settings = SupportSettingsSnapshot(
                language = request.language.name,
                openClawBackend = request.openClawBackend.name,
                voiceRuntime = request.voiceRuntime.name,
                streamingEnabled = request.streamingEnabled,
                chatMemoryEnabled = request.chatMemoryEnabled,
                performanceMode = request.performanceMode,
                accelerationMode = request.accelerationMode
            ),
            installedModels = request.installedModels.map { model ->
                SupportModelSnapshot(
                    id = model.id,
                    modelName = model.modelName,
                    providerType = model.providerType.name,
                    pathType = model.pathType.name,
                    fileSize = model.fileSize,
                    isActive = model.isActive,
                    pathHint = pathHint(model.modelPath)
                )
            },
            userComment = commentText,
            commentPreview = commentPreview(commentText),
            attachments = attachments
        )

        val manifestFile = File(baseDir, "manifest.json")
        manifestFile.writeText(json.encodeToString(manifest), Charsets.UTF_8)

        val zipEntries = attachmentFiles + manifestFile
        ZipOutputStream(FileOutputStream(archiveFile)).use { zip ->
            zipEntries.forEach { file ->
                zip.putNextEntry(ZipEntry(file.name))
                FileInputStream(file).use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }

        val archiveSha = sha256(archiveFile)
        return SupportReportBundle(
            ticketId = ticketId,
            archiveFile = archiveFile,
            archiveSha256 = archiveSha,
            shareText = buildShareText(ticketId, archiveSha, commentText),
            telegramStartPayload = telegramStartPayload(ticketId)
        )
    }

    private fun buildAppDiagnosticsText(ticketId: String, installationId: String): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        return buildString {
            appendLine("ticket_id=$ticketId")
            appendLine("installation_id=$installationId")
            appendLine("package=${context.packageName}")
            appendLine("version_name=${packageInfo.versionName ?: "unknown"}")
            appendLine("version_code=${packageInfo.longVersionCode}")
            appendLine("first_install_time=${packageInfo.firstInstallTime}")
            appendLine("last_update_time=${packageInfo.lastUpdateTime}")
            appendLine("signer_sha256=${signerSha256(packageInfo).orEmpty()}")
            appendLine("process_pid=${Process.myPid()}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("sdk_int=${Build.VERSION.SDK_INT}")
            appendLine("android_release=${Build.VERSION.RELEASE ?: "unknown"}")
            appendLine("supported_abis=${Build.SUPPORTED_ABIS.joinToString()}")
        }
    }

    private fun formatVaultLogs(logs: List<LogEntry>): String {
        if (logs.isEmpty()) {
            return "No internal VaultLogger entries recorded."
        }
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return buildString {
            logs.sortedByDescending { it.timestamp }.forEach { entry ->
                append('[')
                append(formatter.format(Date(entry.timestamp)))
                append("] ")
                append(entry.level.prefix)
                append('/')
                append(entry.tag)
                append(": ")
                appendLine(entry.message)
                entry.stackTrace?.takeIf { it.isNotBlank() }?.let {
                    appendLine(it)
                }
            }
        }
    }

    private fun captureOwnProcessLogcat(): String {
        return runCatching {
            val process = ProcessBuilder(
                "logcat",
                "-d",
                "-t",
                "400",
                "--pid=${Process.myPid()}"
            )
                .redirectErrorStream(true)
                .start()
            process.waitFor(2, TimeUnit.SECONDS)
            process.inputStream.bufferedReader().use { it.readText() }
                .ifBlank { "logcat returned no lines for current process." }
        }.getOrElse { error ->
            "logcat capture unavailable: ${error.message ?: error::class.java.simpleName}"
        }
    }

    private fun readOrCreateInstallationId(): String {
        val prefs = context.getSharedPreferences(SUPPORT_PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(SUPPORT_INSTALLATION_ID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(SUPPORT_INSTALLATION_ID, created).apply()
        return created
    }

    private fun signerSha256(packageInfo: android.content.pm.PackageInfo): String? {
        val signature = packageInfo.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            ?: return null
        return sha256(signature)
    }

    private fun cleanOldReports() {
        val root = File(context.cacheDir, "support-reports")
        if (!root.exists()) return
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3)
        root.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) {
                file.deleteRecursively()
            }
        }
    }

    private fun pathHint(rawPath: String): String {
        if (rawPath.startsWith("content://", ignoreCase = true)) return "content-uri"
        val normalized = rawPath.replace('\\', '/')
        return normalized.substringAfterLast('/').ifBlank { "unknown" }
    }

    private fun buildTicketId(timestamp: Long): String {
        val prefix = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(timestamp))
        val tail = UUID.randomUUID().toString().substring(0, 8)
        return "$prefix-$tail"
    }

    companion object {
        fun telegramStartPayload(ticketId: String): String {
            val compact = ticketId.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "")
            return "support_${compact.take(48)}"
        }

        fun commentPreview(comment: String, maxLength: Int = 120): String {
            val normalized = comment.trim().replace(Regex("\\s+"), " ")
            if (normalized.isEmpty()) return ""
            return if (normalized.length <= maxLength) normalized else normalized.take(maxLength - 3) + "..."
        }

        fun buildShareText(ticketId: String, archiveSha256: String, userComment: String): String {
            val preview = commentPreview(userComment)
            return buildString {
                appendLine("Support ticket: $ticketId")
                appendLine("Bot: @$SUPPORT_BOT_USERNAME")
                appendLine("Archive SHA-256: $archiveSha256")
                if (preview.isNotBlank()) {
                    appendLine("Comment: $preview")
                }
                append("Open @$SUPPORT_BOT_USERNAME and send this archive.")
            }
        }

        fun sha256(file: File): String = FileInputStream(file).use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().toHex()
        }

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
