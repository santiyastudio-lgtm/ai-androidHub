package com.santiya.localaihub.plugins

import android.content.Context
import androidx.compose.runtime.Composable
import com.santiya.localaihub.global.AppPaths
import com.santiya.localaihub.models.plugins.PluginInfo
import com.santiya.localaihub.plugins.api.SuperPlugin
import com.santiya.localaihub.termux.TermuxBridge
import com.santiya.localaihub.termux.TermuxCommandRequest
import com.santiya.localaihub.termux.TermuxCommandResult
import com.dark.gguf_lib.toolcalling.ToolCall
import com.dark.gguf_lib.toolcalling.ToolDefinitionBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

data class TermuxToolResult(
    val action: String,
    val success: Boolean,
    val message: String,
    val commandPath: String? = null,
    val workdir: String? = null,
    val stdout: String? = null,
    val stderr: String? = null,
    val exitCode: Int? = null,
    val termuxErrorCode: Int? = null,
    val termuxErrorMessage: String? = null,
    val durationMs: Long? = null,
)

class TermuxPlugin(
    private val context: Context,
    private val workspaceRoot: File = AppPaths.workspaceFiles(context),
) : SuperPlugin {

    companion object {
        private const val PLUGIN_NAME = "Termux"
        private const val TOOL_STATUS = "termux_status"
        private const val TOOL_EXEC = "termux_exec"
        private const val TOOL_RUN_WORKSPACE_FILE = "termux_run_workspace_file"
        private const val DEFAULT_TIMEOUT_SECONDS = 30L
        private const val MAX_OUTPUT_CHARS = 16_000
    }

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo(
            name = PLUGIN_NAME,
            description = "Run local Linux-style commands through Termux and execute workspace scripts via stdin",
            author = "SantiyaLocalAiHub",
            version = "1.0.0",
            toolDefinitionBuilder = listOf(
                ToolDefinitionBuilder(
                    TOOL_STATUS,
                    "Check whether Termux integration is installed, visible, and ready for command execution"
                ),
                ToolDefinitionBuilder(
                    TOOL_EXEC,
                    "Execute a command through Termux. Use this for shell, python, node, git, or package tasks inside Termux."
                )
                    .stringParam("command_path", "Absolute or \$PREFIX-based command path, for example '\$PREFIX/bin/bash' or '\$PREFIX/bin/python'", required = true)
                    .stringParam("arguments", "Optional arguments. Pass a JSON array string or a shell-style string.", required = false)
                    .stringParam("stdin", "Optional stdin payload. Useful for inline shell or python code.", required = false)
                    .stringParam("workdir", "Termux working directory. Default: ~/ ", required = false)
                    .numberParam("timeout_seconds", "Callback timeout in seconds. Default: 30", required = false),
                ToolDefinitionBuilder(
                    TOOL_RUN_WORKSPACE_FILE,
                    "Read a text file from the app workspace sandbox and run it through a Termux interpreter via stdin"
                )
                    .stringParam("path", "Relative path to a text file inside the app workspace sandbox", required = true)
                    .stringParam("command_path", "Interpreter command path, for example '\$PREFIX/bin/bash' or '\$PREFIX/bin/python'", required = true)
                    .stringParam("arguments", "Optional interpreter arguments. Pass a JSON array string or a shell-style string.", required = false)
                    .stringParam("workdir", "Termux working directory. Default: ~/ ", required = false)
                    .numberParam("timeout_seconds", "Callback timeout in seconds. Default: 30", required = false)
            )
        )
    }

    override suspend fun executeTool(toolCall: ToolCall): Result<Any> {
        return when (toolCall.name) {
            TOOL_STATUS -> Result.success(statusResult())
            TOOL_EXEC -> runCommand(toolCall)
            TOOL_RUN_WORKSPACE_FILE -> runWorkspaceFile(toolCall)
            else -> Result.failure(IllegalArgumentException("Unknown tool: ${toolCall.name}"))
        }
    }

    override fun serializeResult(data: Any): String {
        return (data as? TermuxToolResult)?.let {
            JSONObject().apply {
                put("action", it.action)
                put("success", it.success)
                put("message", it.message)
                put("commandPath", it.commandPath)
                put("workdir", it.workdir)
                put("stdout", it.stdout)
                put("stderr", it.stderr)
                put("exitCode", it.exitCode)
                put("termuxErrorCode", it.termuxErrorCode)
                put("termuxErrorMessage", it.termuxErrorMessage)
                put("durationMs", it.durationMs)
            }.toString()
        } ?: data.toString()
    }

    @Composable
    override fun ToolCallUI() = Unit

    @Composable
    override fun CacheToolUI(data: JSONObject) = Unit

    private fun statusResult(): TermuxToolResult {
        val status = TermuxBridge.getStatus(context)
        return TermuxToolResult(
            action = TOOL_STATUS,
            success = status.ready,
            message = status.message,
        )
    }

    private suspend fun runCommand(toolCall: ToolCall): Result<Any> = withContext(Dispatchers.IO) {
        val commandPath = normalizeCommandPath(toolCall.getString("command_path"))
        val arguments = parseArguments(toolCall.getString("arguments", ""))
        val stdin = toolCall.getString("stdin", "").ifBlank { null }
        val workdir = toolCall.getString("workdir", "~/").ifBlank { "~/" }
        val timeoutSeconds = toolCall.getInt("timeout_seconds", DEFAULT_TIMEOUT_SECONDS.toInt())
            .toLong()
            .coerceIn(1L, 180L)

        runCatching {
            val result = TermuxBridge.runCommand(
                context = context,
                request = TermuxCommandRequest(
                    commandPath = commandPath,
                    arguments = arguments,
                    stdin = stdin,
                    workdir = workdir,
                    timeoutMs = timeoutSeconds * 1000L,
                )
            )
            result.toToolResult(action = TOOL_EXEC)
        }
    }

    private suspend fun runWorkspaceFile(toolCall: ToolCall): Result<Any> = withContext(Dispatchers.IO) {
        val path = toolCall.getString("path")
        val interpreter = normalizeCommandPath(toolCall.getString("command_path"))
        val arguments = parseArguments(toolCall.getString("arguments", ""))
        val workdir = toolCall.getString("workdir", "~/").ifBlank { "~/" }
        val timeoutSeconds = toolCall.getInt("timeout_seconds", DEFAULT_TIMEOUT_SECONDS.toInt())
            .toLong()
            .coerceIn(1L, 180L)

        val file = resolveSandboxPath(path)
        if (!file.exists()) {
            return@withContext Result.failure(FileNotFoundException("Workspace file not found: ${file.absolutePath}"))
        }
        if (!file.isFile) {
            return@withContext Result.failure(IllegalArgumentException("Path is not a file: ${file.absolutePath}"))
        }

        val scriptBody = file.readText(Charsets.UTF_8)
        runCatching {
            val result = TermuxBridge.runCommand(
                context = context,
                request = TermuxCommandRequest(
                    commandPath = interpreter,
                    arguments = arguments,
                    stdin = scriptBody,
                    workdir = workdir,
                    timeoutMs = timeoutSeconds * 1000L,
                    description = "Run workspace file ${file.name}",
                )
            )
            result.toToolResult(
                action = TOOL_RUN_WORKSPACE_FILE,
                messagePrefix = "Ran workspace file ${file.name} through Termux"
            )
        }
    }

    private fun TermuxCommandResult.toToolResult(
        action: String,
        messagePrefix: String = "Termux command finished",
    ): TermuxToolResult {
        val trimmedStdout = stdout.limitOutput()
        val trimmedStderr = stderr.limitOutput()
        val message = when {
            success -> "$messagePrefix successfully."
            !termuxErrorMessage.isNullOrBlank() -> "Termux returned an integration error: $termuxErrorMessage"
            exitCode != null -> "$messagePrefix with exit code $exitCode."
            else -> "$messagePrefix, but no exit code was reported."
        }
        return TermuxToolResult(
            action = action,
            success = success,
            message = message,
            commandPath = commandPath,
            workdir = workdir,
            stdout = trimmedStdout,
            stderr = trimmedStderr,
            exitCode = exitCode,
            termuxErrorCode = termuxErrorCode,
            termuxErrorMessage = termuxErrorMessage,
            durationMs = durationMs,
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

    private fun normalizeCommandPath(raw: String): String {
        val trimmed = raw.trim()
        require(trimmed.isNotBlank()) { "command_path is required" }
        return when (trimmed) {
            "bash" -> "\$PREFIX/bin/bash"
            "sh" -> "\$PREFIX/bin/sh"
            "python", "python3" -> "\$PREFIX/bin/python"
            "node" -> "\$PREFIX/bin/node"
            else -> trimmed
        }
    }

    private fun parseArguments(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return emptyList()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return runCatching {
                val array = org.json.JSONArray(trimmed)
                buildList {
                    for (index in 0 until array.length()) {
                        add(array.getString(index))
                    }
                }
            }.getOrDefault(emptyList())
        }

        val matches = Regex("""[^\s"']+|"([^"]*)"|'([^']*)'""")
            .findAll(trimmed)
            .map { match ->
                match.groups[1]?.value
                    ?: match.groups[2]?.value
                    ?: match.value
            }
            .toList()
        return matches.filter { it.isNotBlank() }
    }

    private fun String.limitOutput(): String {
        return if (length > MAX_OUTPUT_CHARS) {
            take(MAX_OUTPUT_CHARS) + "\n... [truncated]"
        } else {
            this
        }
    }
}
