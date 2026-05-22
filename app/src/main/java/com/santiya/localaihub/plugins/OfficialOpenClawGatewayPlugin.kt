package com.santiya.localaihub.plugins

import android.content.Context
import androidx.compose.runtime.Composable
import com.dark.gguf_lib.toolcalling.ToolCall
import com.dark.gguf_lib.toolcalling.ToolDefinitionBuilder
import com.santiya.localaihub.global.AppPaths
import com.santiya.localaihub.hub.OpenClawLocalSettingsStore
import com.santiya.localaihub.models.plugins.PluginInfo
import com.santiya.localaihub.openclaw.OfficialOpenClawGatewayClient
import com.santiya.localaihub.openclaw.OfficialOpenClawGatewayTermux
import com.santiya.localaihub.plugins.api.SuperPlugin
import com.santiya.localaihub.termux.TermuxBridge
import com.santiya.localaihub.termux.TermuxCommandRequest
import com.santiya.localaihub.termux.TermuxCommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class OfficialOpenClawGatewayToolResult(
    val action: String,
    val success: Boolean,
    val message: String,
    val endpoint: String? = null,
    val modelId: String? = null,
    val text: String? = null,
    val models: List<String> = emptyList(),
    val payload: String? = null,
    val cmdPath: String? = null,
    val shellPath: String? = null,
    val envPath: String? = null,
    val termuxStdout: String? = null,
    val termuxStderr: String? = null,
    val termuxExitCode: Int? = null,
)

class OfficialOpenClawGatewayPlugin(
    private val context: Context,
    private val client: OfficialOpenClawGatewayClient = OfficialOpenClawGatewayClient(),
) : SuperPlugin {

    companion object {
        const val PLUGIN_NAME = "Official OpenClaw Gateway"
        const val TOOL_STATUS = "openclaw_gateway_status"
        const val TOOL_MODELS = "openclaw_gateway_models"
        const val TOOL_SEND = "openclaw_gateway_send"
        const val TOOL_INVOKE = "openclaw_gateway_tool_invoke"
        const val TOOL_CONNECT_FRAME = "openclaw_gateway_connect_frame"
        const val TOOL_WRITE_SETUP = "openclaw_gateway_write_setup_scripts"
        const val TOOL_TERMUX_BOOTSTRAP = "openclaw_gateway_termux_bootstrap"
        const val TOOL_TERMUX_START = "openclaw_gateway_termux_start"
        const val TOOL_TERMUX_STATUS = "openclaw_gateway_termux_status"
        const val TOOL_TERMUX_CALL = "openclaw_gateway_termux_call"
        private const val MAX_TERMUX_OUTPUT_CHARS = 16_000
    }

    private val settingsStore = OpenClawLocalSettingsStore(context)

    override fun getPluginInfo(): PluginInfo = PluginInfo(
        name = PLUGIN_NAME,
        description = "Connect to the official openclaw/openclaw Gateway over its local/private HTTP and WS protocol surfaces",
        author = "SantiyaLocalAiHub",
        version = "1.0.0",
        toolDefinitionBuilder = listOf(
            ToolDefinitionBuilder(TOOL_STATUS, "Check official OpenClaw Gateway readiness")
                .stringParam("base_url", "Gateway URL, default from OpenClaw settings", required = false),
            ToolDefinitionBuilder(TOOL_MODELS, "List official OpenClaw Gateway /v1/models entries")
                .stringParam("base_url", "Gateway URL, default from OpenClaw settings", required = false),
            ToolDefinitionBuilder(TOOL_SEND, "Send a message through official OpenClaw Gateway /v1/chat/completions")
                .stringParam("prompt", "User prompt", required = true)
                .stringParam("system_prompt", "Optional system prompt", required = false)
                .stringParam("model_id", "Gateway model id, default openclaw/default", required = false)
                .stringParam("base_url", "Gateway URL, default from OpenClaw settings", required = false)
                .numberParam("max_tokens", "Maximum output tokens", required = false),
            ToolDefinitionBuilder(TOOL_INVOKE, "Invoke an official OpenClaw Gateway tool through /tools/invoke")
                .stringParam("tool_name", "Tool name as exposed by the Gateway", required = true)
                .stringParam("arguments_json", "Tool arguments as JSON object", required = false)
                .stringParam("base_url", "Gateway URL, default from OpenClaw settings", required = false),
            ToolDefinitionBuilder(TOOL_CONNECT_FRAME, "Build an official Gateway WebSocket operator connect frame for diagnostics")
                .stringParam("token", "Optional gateway token", required = false),
            ToolDefinitionBuilder(TOOL_WRITE_SETUP, "Write official OpenClaw Gateway setup scripts for Windows and Termux/Linux"),
            ToolDefinitionBuilder(TOOL_TERMUX_BOOTSTRAP, "Install Node.js and the official openclaw CLI inside Termux")
                .numberParam("port", "Gateway port, default 18789", required = false)
                .stringParam("token", "Optional gateway token", required = false),
            ToolDefinitionBuilder(TOOL_TERMUX_START, "Start the official OpenClaw Gateway locally inside Termux")
                .numberParam("port", "Gateway port, default 18789", required = false)
                .stringParam("token", "Optional gateway token", required = false),
            ToolDefinitionBuilder(TOOL_TERMUX_STATUS, "Probe the local official OpenClaw Gateway through Termux CLI WebSocket RPC")
                .numberParam("port", "Gateway port, default 18789", required = false)
                .stringParam("token", "Optional gateway token", required = false),
            ToolDefinitionBuilder(TOOL_TERMUX_CALL, "Call a raw official OpenClaw Gateway RPC method through the Termux CLI")
                .stringParam("method", "RPC method, for example status or logs.tail", required = true)
                .stringParam("params_json", "JSON object params, default {}", required = false)
                .numberParam("port", "Gateway port, default 18789", required = false)
                .stringParam("token", "Optional gateway token", required = false),
        )
    )

    override suspend fun executeTool(toolCall: ToolCall): Result<Any> = when (toolCall.name) {
        TOOL_STATUS -> status(toolCall)
        TOOL_MODELS -> models(toolCall)
        TOOL_SEND -> send(toolCall)
        TOOL_INVOKE -> invoke(toolCall)
        TOOL_CONNECT_FRAME -> connectFrame(toolCall)
        TOOL_WRITE_SETUP -> writeSetupScripts()
        TOOL_TERMUX_BOOTSTRAP -> termuxBootstrap(toolCall)
        TOOL_TERMUX_START -> termuxStart(toolCall)
        TOOL_TERMUX_STATUS -> termuxStatus(toolCall)
        TOOL_TERMUX_CALL -> termuxCall(toolCall)
        else -> Result.failure(IllegalArgumentException("Unknown official OpenClaw Gateway tool: ${toolCall.name}"))
    }

    override fun serializeResult(data: Any): String {
        return (data as? OfficialOpenClawGatewayToolResult)?.let {
            JSONObject().apply {
                put("plugin", PLUGIN_NAME)
                put("action", it.action)
                put("success", it.success)
                put("message", it.message)
                put("endpoint", it.endpoint)
                put("modelId", it.modelId)
                put("text", it.text)
                put("models", JSONArray(it.models))
                put("payload", it.payload)
                put("cmdPath", it.cmdPath)
                put("shellPath", it.shellPath)
                put("envPath", it.envPath)
                put("termuxStdout", it.termuxStdout)
                put("termuxStderr", it.termuxStderr)
                put("termuxExitCode", it.termuxExitCode)
            }.toString()
        } ?: data.toString()
    }

    @Composable
    override fun ToolCallUI() = Unit

    @Composable
    override fun CacheToolUI(data: JSONObject) = Unit

    private suspend fun status(toolCall: ToolCall): Result<Any> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = settingsStore.read()
            val endpoint = toolCall.getString("base_url", "").ifBlank { settings.officialGatewayEndpoint }
            val status = client.status(endpoint, settings.officialGatewayToken)
            OfficialOpenClawGatewayToolResult(
                action = TOOL_STATUS,
                success = status.reachable,
                message = status.message,
                endpoint = status.endpoint,
                models = status.modelIds,
            )
        }
    }

    private suspend fun models(toolCall: ToolCall): Result<Any> = status(toolCall).map { result ->
        (result as OfficialOpenClawGatewayToolResult).copy(action = TOOL_MODELS)
    }

    private suspend fun send(toolCall: ToolCall): Result<Any> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = settingsStore.read()
            val endpoint = toolCall.getString("base_url", "").ifBlank { settings.officialGatewayEndpoint }
            val modelId = toolCall.getString("model_id", "").ifBlank {
                settings.officialGatewayModelId.ifBlank { OfficialOpenClawGatewayClient.DEFAULT_MODEL_ID }
            }
            val messages = buildList {
                val systemPrompt = toolCall.getString("system_prompt", "")
                if (systemPrompt.isNotBlank()) add(JSONObject().put("role", "system").put("content", systemPrompt))
                add(JSONObject().put("role", "user").put("content", toolCall.getString("prompt")))
            }
            val response = client.chatCompletion(
                rawEndpoint = endpoint,
                token = settings.officialGatewayToken,
                modelId = modelId,
                messages = messages,
                maxTokens = toolCall.getInt("max_tokens", 2048),
            )
            OfficialOpenClawGatewayToolResult(
                action = TOOL_SEND,
                success = true,
                message = "Official OpenClaw Gateway completed the chat request.",
                endpoint = response.endpoint,
                modelId = response.modelId,
                text = response.text,
            )
        }
    }

    private suspend fun invoke(toolCall: ToolCall): Result<Any> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = settingsStore.read()
            val endpoint = toolCall.getString("base_url", "").ifBlank { settings.officialGatewayEndpoint }
            val args = runCatching { JSONObject(toolCall.getString("arguments_json", "{}")) }.getOrElse { JSONObject() }
            val payload = client.invokeTool(
                rawEndpoint = endpoint,
                token = settings.officialGatewayToken,
                toolName = toolCall.getString("tool_name"),
                arguments = args,
            )
            OfficialOpenClawGatewayToolResult(
                action = TOOL_INVOKE,
                success = true,
                message = "Official OpenClaw Gateway tool invocation completed.",
                endpoint = endpoint,
                payload = payload.toString(),
            )
        }
    }

    private fun connectFrame(toolCall: ToolCall): Result<Any> = runCatching {
        val settings = settingsStore.read()
        val token = toolCall.getString("token", "").ifBlank { settings.officialGatewayToken }
        OfficialOpenClawGatewayToolResult(
            action = TOOL_CONNECT_FRAME,
            success = true,
            message = "Official Gateway WebSocket operator connect frame built for diagnostics.",
            endpoint = OfficialOpenClawGatewayClient.toWebSocketUrl(settings.officialGatewayEndpoint),
            payload = OfficialOpenClawGatewayClient.buildOperatorConnectFrame(token),
        )
    }

    private suspend fun writeSetupScripts(): Result<Any> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(AppPaths.workspaceFiles(context), "official-openclaw-gateway").also { it.mkdirs() }
            val cmd = File(dir, "start-openclaw-gateway.cmd")
            val sh = File(dir, "start-openclaw-gateway.sh")
            val env = File(dir, "openclaw-gateway.env.example")
            cmd.writeText(windowsScript(), Charsets.UTF_8)
            sh.writeText(shellScript(), Charsets.UTF_8)
            env.writeText(envExample(), Charsets.UTF_8)
            OfficialOpenClawGatewayToolResult(
                action = TOOL_WRITE_SETUP,
                success = true,
                message = "Official OpenClaw Gateway setup scripts were written. They install/use the official npm package and run `openclaw gateway --bind loopback --port 18789 --allow-unconfigured`.",
                cmdPath = cmd.absolutePath,
                shellPath = sh.absolutePath,
                envPath = env.absolutePath,
            )
        }
    }

    private suspend fun termuxBootstrap(toolCall: ToolCall): Result<Any> = runTermuxScript(
        action = TOOL_TERMUX_BOOTSTRAP,
        messagePrefix = "Official OpenClaw Gateway Termux bootstrap",
        port = gatewayPort(toolCall),
        script = OfficialOpenClawGatewayTermux.bootstrapScript(
            port = gatewayPort(toolCall),
            token = toolToken(toolCall),
        ),
        timeoutMs = 10 * 60 * 1000L,
    )

    private suspend fun termuxStart(toolCall: ToolCall): Result<Any> = runTermuxScript(
        action = TOOL_TERMUX_START,
        messagePrefix = "Official OpenClaw Gateway Termux start",
        port = gatewayPort(toolCall),
        script = OfficialOpenClawGatewayTermux.startScript(
            port = gatewayPort(toolCall),
            token = toolToken(toolCall),
        ),
        timeoutMs = 90 * 1000L,
    )

    private suspend fun termuxStatus(toolCall: ToolCall): Result<Any> = runTermuxScript(
        action = TOOL_TERMUX_STATUS,
        messagePrefix = "Official OpenClaw Gateway Termux status",
        port = gatewayPort(toolCall),
        script = OfficialOpenClawGatewayTermux.statusScript(
            port = gatewayPort(toolCall),
            token = toolToken(toolCall),
        ),
        timeoutMs = 45 * 1000L,
    )

    private suspend fun termuxCall(toolCall: ToolCall): Result<Any> = runTermuxScript(
        action = TOOL_TERMUX_CALL,
        messagePrefix = "Official OpenClaw Gateway Termux RPC call",
        port = gatewayPort(toolCall),
        script = OfficialOpenClawGatewayTermux.gatewayCallScript(
            method = toolCall.getString("method"),
            paramsJson = toolCall.getString("params_json", "{}"),
            port = gatewayPort(toolCall),
            token = toolToken(toolCall),
        ),
        timeoutMs = 180 * 1000L,
    )

    private suspend fun runTermuxScript(
        action: String,
        messagePrefix: String,
        port: Int,
        script: String,
        timeoutMs: Long,
    ): Result<Any> = withContext(Dispatchers.IO) {
        runCatching {
            val result = TermuxBridge.runCommand(
                context = context,
                request = TermuxCommandRequest(
                    commandPath = OfficialOpenClawGatewayTermux.TERMUX_SHELL,
                    stdin = script,
                    background = false,
                    label = "OpenClaw Gateway",
                    description = messagePrefix,
                    timeoutMs = timeoutMs,
                )
            )
            result.toGatewayResult(action, messagePrefix, port)
        }
    }

    private fun gatewayPort(toolCall: ToolCall): Int =
        toolCall.getInt("port", OfficialOpenClawGatewayTermux.DEFAULT_PORT)
            .coerceIn(1024, 65535)

    private fun toolToken(toolCall: ToolCall): String {
        val settings = settingsStore.read()
        return toolCall.getString("token", "").ifBlank { settings.officialGatewayToken }
    }

    private fun TermuxCommandResult.toGatewayResult(
        action: String,
        messagePrefix: String,
        port: Int,
    ): OfficialOpenClawGatewayToolResult {
        val stdout = termuxOutput(stdout)
        val stderr = termuxOutput(stderr)
        val message = when {
            success -> "$messagePrefix completed."
            !termuxErrorMessage.isNullOrBlank() -> "$messagePrefix failed: $termuxErrorMessage"
            exitCode != null -> "$messagePrefix finished with exit code $exitCode."
            else -> "$messagePrefix did not return an exit code."
        }
        return OfficialOpenClawGatewayToolResult(
            action = action,
            success = success,
            message = message,
            endpoint = "http://127.0.0.1:$port",
            termuxStdout = stdout,
            termuxStderr = stderr,
            termuxExitCode = exitCode,
        )
    }

    private fun termuxOutput(value: String): String =
        if (value.length > MAX_TERMUX_OUTPUT_CHARS) {
            value.take(MAX_TERMUX_OUTPUT_CHARS) + "\n... [truncated]"
        } else {
            value
        }

    private fun windowsScript(): String = """
        @echo off
        setlocal
        if "%OPENCLAW_GATEWAY_PORT%"=="" set OPENCLAW_GATEWAY_PORT=18789
        where openclaw >NUL 2>NUL
        if errorlevel 1 (
          where npm >NUL 2>NUL || (echo Node.js/npm is required for official OpenClaw Gateway && exit /b 1)
          npm install -g openclaw@latest
        )
          openclaw gateway --bind loopback --port %OPENCLAW_GATEWAY_PORT% --allow-unconfigured --verbose
    """.trimIndent() + "\n"

    private fun shellScript(): String = """
        #!/usr/bin/env sh
        set -eu
        : "${'$'}{OPENCLAW_GATEWAY_PORT:=18789}"
        if ! command -v openclaw >/dev/null 2>&1; then
          command -v npm >/dev/null 2>&1 || { echo "Node.js/npm is required for official OpenClaw Gateway"; exit 1; }
          npm install -g openclaw@latest
        fi
        exec openclaw gateway --bind loopback --port "${'$'}OPENCLAW_GATEWAY_PORT" --allow-unconfigured --verbose
    """.trimIndent() + "\n"

    private fun envExample(): String = """
        # Optional shared-secret auth for official OpenClaw Gateway.
        # Keep this local/private. Do not commit real tokens.
        OPENCLAW_GATEWAY_PORT=18789
        OPENCLAW_GATEWAY_TOKEN=
        OPENCLAW_GATEWAY_PASSWORD=
    """.trimIndent() + "\n"
}
