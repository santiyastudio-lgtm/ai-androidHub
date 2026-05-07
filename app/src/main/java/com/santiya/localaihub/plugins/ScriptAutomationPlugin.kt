package com.santiya.localaihub.plugins

import androidx.compose.runtime.Composable
import com.santiya.localaihub.global.AppPaths
import com.santiya.localaihub.models.plugins.PluginInfo
import com.santiya.localaihub.plugins.api.SuperPlugin
import com.dark.gguf_lib.toolcalling.ToolCall
import com.dark.gguf_lib.toolcalling.ToolDefinitionBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit

data class ScriptExecutionResult(
    val path: String,
    val interpreter: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val success: Boolean,
)

class ScriptAutomationPlugin(
    private val workspaceRoot: File,
) : SuperPlugin {

    companion object {
        const val TOOL_EXECUTE_SCRIPT = "execute_script"
        private const val DEFAULT_TIMEOUT_SECONDS = 20L
        private const val MAX_OUTPUT_CHARS = 12_000
    }

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo(
            name = "Automation",
            description = "Execute shell scripts stored inside the app workspace sandbox",
            author = "SantiyaLocalAiHub",
            version = "1.0.0",
            toolDefinitionBuilder = listOf(
                ToolDefinitionBuilder(
                    TOOL_EXECUTE_SCRIPT,
                    "Execute a shell script from the app sandbox. Use create_file first to write the script."
                )
                    .stringParam("path", "Relative path to a shell script inside the app workspace sandbox", required = true)
                    .stringParam("interpreter", "Interpreter to use. Default: sh", required = false)
                    .numberParam("timeout_seconds", "Execution timeout in seconds. Default: 20", required = false)
            )
        )
    }

    override suspend fun executeTool(toolCall: ToolCall): Result<Any> {
        return when (toolCall.name) {
            TOOL_EXECUTE_SCRIPT -> executeScript(toolCall)
            else -> Result.failure(IllegalArgumentException("Unknown tool: ${toolCall.name}"))
        }
    }

    override fun serializeResult(data: Any): String {
        return (data as? ScriptExecutionResult)?.let {
            JSONObject().apply {
                put("path", it.path)
                put("interpreter", it.interpreter)
                put("exitCode", it.exitCode)
                put("stdout", it.stdout)
                put("stderr", it.stderr)
                put("durationMs", it.durationMs)
                put("success", it.success)
            }.toString()
        } ?: data.toString()
    }

    @Composable
    override fun ToolCallUI() = Unit

    @Composable
    override fun CacheToolUI(data: JSONObject) = Unit

    private suspend fun executeScript(toolCall: ToolCall): Result<Any> = withContext(Dispatchers.IO) {
        val rawPath = toolCall.getString("path")
        val interpreter = toolCall.getString("interpreter", "sh").trim().ifBlank { "sh" }
        val timeoutSeconds = toolCall.getInt("timeout_seconds", DEFAULT_TIMEOUT_SECONDS.toInt())
            .toLong()
            .coerceIn(1L, 120L)

        val file = resolveSandboxPath(rawPath)
        if (!file.exists()) {
            return@withContext Result.failure(FileNotFoundException("Script not found: ${file.absolutePath}"))
        }
        if (!file.isFile) {
            return@withContext Result.failure(IllegalArgumentException("Path is not a file: ${file.absolutePath}"))
        }

        val command = when (interpreter.lowercase()) {
            "sh", "/system/bin/sh" -> listOf("/system/bin/sh", file.absolutePath)
            else -> return@withContext Result.failure(
                IllegalArgumentException("Unsupported interpreter: $interpreter. Only 'sh' is supported on Android.")
            )
        }

        val startedAt = System.currentTimeMillis()
        val process = ProcessBuilder(command)
            .directory(workspaceRoot)
            .redirectErrorStream(false)
            .start()

        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return@withContext Result.success(
                ScriptExecutionResult(
                    path = file.absolutePath,
                    interpreter = interpreter,
                    exitCode = -1,
                    stdout = "",
                    stderr = "Execution timed out after ${timeoutSeconds}s",
                    durationMs = System.currentTimeMillis() - startedAt,
                    success = false,
                )
            )
        }

        val stdout = process.inputStream.bufferedReader().use { it.readText() }.take(MAX_OUTPUT_CHARS)
        val stderr = process.errorStream.bufferedReader().use { it.readText() }.take(MAX_OUTPUT_CHARS)
        val exitCode = process.exitValue()

        Result.success(
            ScriptExecutionResult(
                path = file.absolutePath,
                interpreter = interpreter,
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr,
                durationMs = System.currentTimeMillis() - startedAt,
                success = exitCode == 0,
            )
        )
    }

    private fun resolveSandboxPath(path: String): File {
        val candidate = File(path).let { file ->
            if (file.isAbsolute) file else File(workspaceRoot, path)
        }
        val resolved = candidate.canonicalFile
        val sandbox = workspaceRoot.canonicalFile
        require(resolved.path.startsWith(sandbox.path)) {
            "Access denied: path must stay inside ${sandbox.path}"
        }
        return resolved
    }
}
