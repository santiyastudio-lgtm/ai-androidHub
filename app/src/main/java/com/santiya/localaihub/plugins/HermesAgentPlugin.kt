package com.santiya.localaihub.plugins

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import com.dark.gguf_lib.toolcalling.ToolCall
import com.dark.gguf_lib.toolcalling.ToolDefinitionBuilder
import com.santiya.localaihub.browser.BrowserToolState
import com.santiya.localaihub.browser.BrowserUrlPolicy
import com.santiya.localaihub.global.AppPaths
import com.santiya.localaihub.models.plugins.PluginInfo
import com.santiya.localaihub.plugins.api.SuperPlugin
import com.santiya.localaihub.termux.TermuxBridge
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class HermesAgentResult(
    val action: String,
    val success: Boolean,
    val message: String,
    val path: String? = null,
    val url: String? = null,
    val locationEnabled: Boolean? = null,
    val content: String? = null,
    val files: List<String>? = null,
    val exitCode: Int? = null,
    val stdout: String? = null,
    val stderr: String? = null,
    val durationMs: Long? = null,
)

class HermesAgentPlugin(
    private val context: Context,
) : SuperPlugin {

    companion object {
        private const val PLUGIN_NAME = "Hermes Agent"
        const val TOOL_STATUS = "hermes_status"
        const val TOOL_OPEN_BROWSER = "hermes_open_browser"
        const val TOOL_WRITE_FILE = "hermes_write_file"
        const val TOOL_READ_FILE = "hermes_read_file"
        const val TOOL_LIST_FILES = "hermes_list_files"
        const val TOOL_RUN_SCRIPT = "hermes_run_script"
        const val TOOL_LOCATION_STATUS = "hermes_location_status"
        const val TOOL_OPEN_LOCATION_SETTINGS = "hermes_open_location_settings"
        private const val MAX_CONTENT_CHARS = 200_000
        private const val MAX_READ_CHARS = 80_000
        private const val MAX_LIST_ENTRIES = 120
        private const val MAX_OUTPUT_CHARS = 12_000
    }

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo(
            name = PLUGIN_NAME,
            description = "Local Hermes tools for browser handoff, file/code output, location status, and sandboxed automation",
            author = "SantiyaLocalAiHub",
            version = "1.0.0",
            toolDefinitionBuilder = listOf(
                ToolDefinitionBuilder(
                    TOOL_STATUS,
                    "Report local Hermes tool readiness inside this Android app"
                ),
                ToolDefinitionBuilder(
                    TOOL_OPEN_BROWSER,
                    "Open an http or https URL in the embedded in-app browser"
                )
                    .stringParam("url", "URL to open", required = true),
                ToolDefinitionBuilder(
                    TOOL_WRITE_FILE,
                    "Save generated code, reports, or notes into the Hermes app workspace sandbox"
                )
                    .stringParam("path", "Relative path under the Hermes workspace, for example 'reports/ai-trends.md'", required = true)
                    .stringParam("content", "Text content to save", required = true)
                    .booleanParam("append", "Append instead of overwriting. Default: false", required = false),
                ToolDefinitionBuilder(
                    TOOL_READ_FILE,
                    "Inspect a text file from the Hermes app workspace sandbox"
                )
                    .stringParam("path", "Relative path under the Hermes workspace", required = true)
                    .numberParam("max_chars", "Maximum characters to read. Default: 12000", required = false),
                ToolDefinitionBuilder(
                    TOOL_LIST_FILES,
                    "List files inside the Hermes app workspace sandbox"
                )
                    .stringParam("path", "Relative directory under the Hermes workspace. Default: root", required = false)
                    .booleanParam("recursive", "List recursively. Default: false", required = false),
                ToolDefinitionBuilder(
                    TOOL_RUN_SCRIPT,
                    "Run a shell script stored inside the Hermes workspace sandbox using Android /system/bin/sh"
                )
                    .stringParam("path", "Relative path to a .sh script under the Hermes workspace", required = true)
                    .numberParam("timeout_seconds", "Timeout in seconds. Default: 20, max: 90", required = false),
                ToolDefinitionBuilder(
                    TOOL_LOCATION_STATUS,
                    "Read whether Android location services are enabled"
                ),
                ToolDefinitionBuilder(
                    TOOL_OPEN_LOCATION_SETTINGS,
                    "Open Android location settings so the user can enable or disable GPS manually"
                )
            )
        )
    }

    override suspend fun executeTool(toolCall: ToolCall): Result<Any> {
        return when (toolCall.name) {
            TOOL_STATUS -> Result.success(status())
            TOOL_OPEN_BROWSER -> openBrowser(toolCall)
            TOOL_WRITE_FILE -> writeFile(toolCall)
            TOOL_READ_FILE -> readFile(toolCall)
            TOOL_LIST_FILES -> listFiles(toolCall)
            TOOL_RUN_SCRIPT -> runScript(toolCall)
            TOOL_LOCATION_STATUS -> Result.success(locationStatus())
            TOOL_OPEN_LOCATION_SETTINGS -> openLocationSettings()
            else -> Result.failure(IllegalArgumentException("Unknown Hermes tool: ${toolCall.name}"))
        }
    }

    override fun serializeResult(data: Any): String {
        return (data as? HermesAgentResult)?.let {
            JSONObject().apply {
                put("plugin", PLUGIN_NAME)
                put("action", it.action)
                put("success", it.success)
                put("message", it.message)
                put("path", it.path)
                put("url", it.url)
                put("locationEnabled", it.locationEnabled)
                put("content", it.content)
                if (it.files != null) put("files", JSONArray(it.files))
                put("exitCode", it.exitCode)
                put("stdout", it.stdout)
                put("stderr", it.stderr)
                put("durationMs", it.durationMs)
            }.toString()
        } ?: data.toString()
    }

    @Composable
    override fun ToolCallUI() = Unit

    @Composable
    override fun CacheToolUI(data: JSONObject) = Unit

    private fun status(): HermesAgentResult {
        val termuxStatus = TermuxBridge.getStatus(context)
        val location = isLocationEnabled()
        return HermesAgentResult(
            action = TOOL_STATUS,
            success = true,
            message = buildString {
                append("Hermes local agent mode is ready. ")
                append("Browser, file write/read/list, sandbox script execution, memory context, and location status tools are registered. ")
                append("Termux: ${termuxStatus.message} ")
                append("Location services: ${if (location) "enabled" else "disabled"}. ")
                append("Android does not allow silent GPS toggling for normal apps; Hermes can open settings or run a sandboxed script when permissions allow it.")
            },
            locationEnabled = location,
        )
    }

    private fun openBrowser(toolCall: ToolCall): Result<Any> {
        return runCatching {
            val url = BrowserUrlPolicy.normalize(toolCall.getString("url"))
            BrowserToolState.openUrl(url)
            HermesAgentResult(
                action = TOOL_OPEN_BROWSER,
                success = true,
                message = "Opened $url in the embedded browser.",
                url = url,
            )
        }
    }

    private suspend fun writeFile(toolCall: ToolCall): Result<Any> = withContext(Dispatchers.IO) {
        runCatching {
            val content = toolCall.getString("content")
            require(content.length <= MAX_CONTENT_CHARS) {
                "Content is too large for one Hermes write (${content.length} chars). Split it into smaller files."
            }
            val file = resolveHermesPath(toolCall.getString("path"))
            file.parentFile?.mkdirs()
            if (toolCall.getBoolean("append", false)) {
                file.appendText(content, Charsets.UTF_8)
            } else {
                file.writeText(content, Charsets.UTF_8)
            }
            HermesAgentResult(
                action = TOOL_WRITE_FILE,
                success = true,
                message = "Saved ${content.length} characters to ${file.name}.",
                path = file.absolutePath,
            )
        }
    }

    private suspend fun readFile(toolCall: ToolCall): Result<Any> = withContext(Dispatchers.IO) {
        runCatching {
            val maxChars = toolCall.getInt("max_chars", 12_000).coerceIn(1, MAX_READ_CHARS)
            val file = resolveHermesPath(toolCall.getString("path"))
            if (!file.exists()) throw FileNotFoundException("Hermes file not found: ${file.absolutePath}")
            require(file.isFile) { "Path is not a file: ${file.absolutePath}" }
            val text = file.readText(Charsets.UTF_8)
            HermesAgentResult(
                action = TOOL_READ_FILE,
                success = true,
                message = "Read ${minOf(text.length, maxChars)} characters from ${file.name}.",
                path = file.absolutePath,
                content = text.take(maxChars) + if (text.length > maxChars) "\n... [truncated]" else "",
            )
        }
    }

    private suspend fun listFiles(toolCall: ToolCall): Result<Any> = withContext(Dispatchers.IO) {
        runCatching {
            val rawPath = toolCall.getString("path", "")
            val recursive = toolCall.getBoolean("recursive", false)
            val dir = if (rawPath.isBlank()) hermesRoot() else resolveHermesPath(rawPath)
            if (!dir.exists()) throw FileNotFoundException("Hermes directory not found: ${dir.absolutePath}")
            require(dir.isDirectory) { "Path is not a directory: ${dir.absolutePath}" }
            val base = hermesRoot()
            val entries = if (recursive) {
                dir.walkTopDown()
                    .filter { it != dir }
                    .take(MAX_LIST_ENTRIES)
                    .map { it.relativeTo(base).path + if (it.isDirectory) "/" else "" }
                    .toList()
            } else {
                dir.listFiles()
                    ?.take(MAX_LIST_ENTRIES)
                    ?.map { it.relativeTo(base).path + if (it.isDirectory) "/" else "" }
                    .orEmpty()
            }
            HermesAgentResult(
                action = TOOL_LIST_FILES,
                success = true,
                message = if (entries.isEmpty()) "Hermes workspace is empty." else "Listed ${entries.size} Hermes workspace entries.",
                path = dir.absolutePath,
                files = entries,
            )
        }
    }

    private suspend fun runScript(toolCall: ToolCall): Result<Any> = withContext(Dispatchers.IO) {
        runCatching {
            val timeoutSeconds = toolCall.getInt("timeout_seconds", 20).coerceIn(1, 90)
            val file = resolveHermesPath(toolCall.getString("path"))
            if (!file.exists()) throw FileNotFoundException("Hermes script not found: ${file.absolutePath}")
            require(file.isFile) { "Path is not a script file: ${file.absolutePath}" }
            require(file.extension.equals("sh", ignoreCase = true)) {
                "Only .sh scripts are allowed for Hermes sandbox execution."
            }
            val startedAt = System.currentTimeMillis()
            val process = ProcessBuilder("/system/bin/sh", file.absolutePath)
                .directory(hermesRoot())
                .redirectErrorStream(false)
                .start()

            val finished = process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@runCatching HermesAgentResult(
                    action = TOOL_RUN_SCRIPT,
                    success = false,
                    message = "Hermes script timed out after ${timeoutSeconds}s.",
                    path = file.absolutePath,
                    exitCode = -1,
                    durationMs = System.currentTimeMillis() - startedAt,
                )
            }

            val stdout = process.inputStream.bufferedReader().use { it.readText() }.take(MAX_OUTPUT_CHARS)
            val stderr = process.errorStream.bufferedReader().use { it.readText() }.take(MAX_OUTPUT_CHARS)
            val exitCode = process.exitValue()
            HermesAgentResult(
                action = TOOL_RUN_SCRIPT,
                success = exitCode == 0,
                message = "Hermes script finished with exit code $exitCode.",
                path = file.absolutePath,
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr,
                durationMs = System.currentTimeMillis() - startedAt,
            )
        }
    }

    private fun locationStatus(): HermesAgentResult {
        val enabled = isLocationEnabled()
        return HermesAgentResult(
            action = TOOL_LOCATION_STATUS,
            success = true,
            message = if (enabled) {
                "Android location services are enabled."
            } else {
                "Android location services are disabled."
            },
            locationEnabled = enabled,
        )
    }

    private fun openLocationSettings(): Result<Any> {
        return runCatching {
            context.startActivity(
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            HermesAgentResult(
                action = TOOL_OPEN_LOCATION_SETTINGS,
                success = true,
                message = "Opened Android location settings.",
                locationEnabled = isLocationEnabled(),
            )
        }
    }

    private fun resolveHermesPath(path: String): File {
        val base = hermesRoot()
        val normalized = path.replace('\\', '/').trim().trimStart('/')
        require(normalized.isNotBlank()) { "path is required" }
        require(".." !in normalized.split('/')) { "Parent directory traversal is not allowed" }
        val resolved = File(base, normalized).canonicalFile
        require(resolved == base || resolved.path.startsWith(base.path + File.separator)) {
            "Access denied: path must stay inside ${base.path}"
        }
        return resolved
    }

    private fun hermesRoot(): File {
        return File(AppPaths.workspaceFiles(context), "hermes").also { it.mkdirs() }.canonicalFile
    }

    private fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return runCatching { manager.isLocationEnabled }.getOrDefault(false)
    }
}
