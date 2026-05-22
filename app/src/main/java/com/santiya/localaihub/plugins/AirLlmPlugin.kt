package com.santiya.localaihub.plugins

import android.content.Context
import androidx.compose.runtime.Composable
import com.dark.gguf_lib.toolcalling.ToolCall
import com.dark.gguf_lib.toolcalling.ToolDefinitionBuilder
import com.santiya.localaihub.airllm.AirLlmGatewayClient
import com.santiya.localaihub.global.AppPaths
import com.santiya.localaihub.hub.OpenClawLocalSettingsStore
import com.santiya.localaihub.models.plugins.PluginInfo
import com.santiya.localaihub.plugins.api.SuperPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AirLlmToolResult(
    val action: String,
    val success: Boolean,
    val message: String,
    val endpoint: String? = null,
    val modelId: String? = null,
    val text: String? = null,
    val models: List<String> = emptyList(),
    val scriptPath: String? = null,
    val shellPath: String? = null,
)

class AirLlmPlugin(
    private val context: Context,
    private val client: AirLlmGatewayClient = AirLlmGatewayClient(),
) : SuperPlugin {

    companion object {
        const val PLUGIN_NAME = "AirLLM Gateway"
        const val TOOL_STATUS = "airllm_status"
        const val TOOL_GENERATE = "airllm_generate"
        const val TOOL_WRITE_GATEWAY_SCRIPT = "airllm_write_gateway_script"
    }

    private val settingsStore = OpenClawLocalSettingsStore(context)

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo(
            name = PLUGIN_NAME,
            description = "Connect OpenClaw to a local or private-LAN AirLLM Python/PyTorch gateway for large Hugging Face models",
            author = "SantiyaLocalAiHub",
            version = "1.0.0",
            toolDefinitionBuilder = listOf(
                ToolDefinitionBuilder(
                    TOOL_STATUS,
                    "Check whether the configured AirLLM gateway is reachable"
                )
                    .stringParam("base_url", "Optional local/private gateway URL. Default comes from OpenClaw settings.", required = false),
                ToolDefinitionBuilder(
                    TOOL_GENERATE,
                    "Generate text through the AirLLM gateway. Use for large HF transformer models when the gateway is running."
                )
                    .stringParam("prompt", "User prompt to send to the AirLLM model", required = true)
                    .stringParam("model_id", "Hugging Face repo id or local model path known to the gateway", required = false)
                    .stringParam("system_prompt", "Optional system prompt", required = false)
                    .stringParam("base_url", "Optional local/private gateway URL. Default comes from OpenClaw settings.", required = false)
                    .numberParam("max_new_tokens", "Maximum new tokens. Default: 256", required = false),
                ToolDefinitionBuilder(
                    TOOL_WRITE_GATEWAY_SCRIPT,
                    "Write a minimal Python AirLLM gateway script into the app workspace for Termux/proot/desktop use"
                )
                    .stringParam("model_id", "Default Hugging Face repo id or local path to load, optional", required = false)
            )
        )
    }

    override suspend fun executeTool(toolCall: ToolCall): Result<Any> {
        return when (toolCall.name) {
            TOOL_STATUS -> status(toolCall)
            TOOL_GENERATE -> generate(toolCall)
            TOOL_WRITE_GATEWAY_SCRIPT -> writeGatewayScript(toolCall)
            else -> Result.failure(IllegalArgumentException("Unknown AirLLM tool: ${toolCall.name}"))
        }
    }

    override fun serializeResult(data: Any): String {
        return (data as? AirLlmToolResult)?.let {
            JSONObject().apply {
                put("plugin", PLUGIN_NAME)
                put("action", it.action)
                put("success", it.success)
                put("message", it.message)
                put("endpoint", it.endpoint)
                put("modelId", it.modelId)
                put("text", it.text)
                put("models", JSONArray(it.models))
                put("scriptPath", it.scriptPath)
                put("shellPath", it.shellPath)
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
            val endpoint = toolCall.getString("base_url", "").ifBlank { settings.airLlmEndpoint }
            val status = client.status(endpoint)
            AirLlmToolResult(
                action = TOOL_STATUS,
                success = status.reachable,
                message = status.message,
                endpoint = status.endpoint,
                models = status.modelIds,
            )
        }
    }

    private suspend fun generate(toolCall: ToolCall): Result<Any> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = settingsStore.read()
            val endpoint = toolCall.getString("base_url", "").ifBlank { settings.airLlmEndpoint }
            val modelId = toolCall.getString("model_id", "").ifBlank { settings.airLlmModelId }
            val response = client.generate(
                rawEndpoint = endpoint,
                modelId = modelId,
                prompt = toolCall.getString("prompt"),
                systemPrompt = toolCall.getString("system_prompt", ""),
                maxNewTokens = toolCall.getInt("max_new_tokens", 256),
            )
            AirLlmToolResult(
                action = TOOL_GENERATE,
                success = true,
                message = "AirLLM generation completed through the configured gateway.",
                endpoint = response.endpoint,
                modelId = response.modelId,
                text = response.text,
            )
        }
    }

    private suspend fun writeGatewayScript(toolCall: ToolCall): Result<Any> = withContext(Dispatchers.IO) {
        runCatching {
            val modelId = toolCall.getString("model_id", "").ifBlank {
                settingsStore.read().airLlmModelId.ifBlank { "Qwen/Qwen2.5-1.5B-Instruct" }
            }
            val dir = File(AppPaths.workspaceFiles(context), "airllm").also { it.mkdirs() }
            val script = File(dir, "airllm_gateway.py")
            val launcher = File(dir, "start_airllm_gateway.sh")
            script.writeText(buildPythonGatewayScript(), Charsets.UTF_8)
            launcher.writeText(buildLauncherScript(modelId), Charsets.UTF_8)
            AirLlmToolResult(
                action = TOOL_WRITE_GATEWAY_SCRIPT,
                success = true,
                message = "AirLLM gateway scripts were written. Run the shell script in Termux/proot/desktop Python after installing torch and airllm.",
                modelId = modelId,
                scriptPath = script.absolutePath,
                shellPath = launcher.absolutePath,
            )
        }
    }

    private fun buildLauncherScript(modelId: String): String = """
        #!/data/data/com.termux/files/usr/bin/sh
        set -eu
        export AIRLLM_MODEL_ID="${modelId.replace("\"", "\\\"")}"
        export AIRLLM_HOST="${'$'}{AIRLLM_HOST:-127.0.0.1}"
        export AIRLLM_PORT="${'$'}{AIRLLM_PORT:-8765}"
        python airllm_gateway.py
    """.trimIndent() + "\n"

    private fun buildPythonGatewayScript(): String = """
        import json
        import os
        from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

        MODEL_ID = os.environ.get("AIRLLM_MODEL_ID", "Qwen/Qwen2.5-1.5B-Instruct")
        HOST = os.environ.get("AIRLLM_HOST", "127.0.0.1")
        PORT = int(os.environ.get("AIRLLM_PORT", "8765"))
        MAX_LENGTH = int(os.environ.get("AIRLLM_MAX_LENGTH", "2048"))
        _model = None

        def load_model(model_id=None):
            global _model, MODEL_ID
            if model_id:
                MODEL_ID = model_id
            if _model is None:
                from airllm import AutoModel
                _model = AutoModel.from_pretrained(MODEL_ID)
            return _model

        def generate_text(prompt, model_id=None, max_new_tokens=256):
            model = load_model(model_id)
            tokens = model.tokenizer(
                [prompt],
                return_tensors="pt",
                return_attention_mask=False,
                truncation=True,
                max_length=MAX_LENGTH,
                padding=False,
            )
            input_ids = tokens["input_ids"]
            try:
                input_ids = input_ids.cuda()
            except Exception:
                pass
            output = model.generate(
                input_ids,
                max_new_tokens=max_new_tokens,
                use_cache=True,
                return_dict_in_generate=True,
            )
            return model.tokenizer.decode(output.sequences[0])

        class Handler(BaseHTTPRequestHandler):
            def _json(self, code, payload):
                data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
                self.send_response(code)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            def do_GET(self):
                if self.path in ("/health", "/"):
                    self._json(200, {"ok": True, "message": "AirLLM gateway ready", "models": [MODEL_ID]})
                elif self.path == "/v1/models":
                    self._json(200, {"object": "list", "data": [{"id": MODEL_ID, "object": "model"}]})
                else:
                    self._json(404, {"error": "not found"})

            def do_POST(self):
                length = int(self.headers.get("Content-Length", "0"))
                body = json.loads(self.rfile.read(length).decode("utf-8") or "{}")
                try:
                    if self.path == "/v1/chat/completions":
                        messages = body.get("messages", [])
                        prompt = "\n".join([m.get("content", "") for m in messages if m.get("content")])
                        model_id = body.get("model") or MODEL_ID
                        text = generate_text(prompt, model_id, int(body.get("max_tokens", 256)))
                        self._json(200, {"choices": [{"message": {"role": "assistant", "content": text}}]})
                    elif self.path == "/generate":
                        text = generate_text(
                            body.get("prompt", ""),
                            body.get("model") or MODEL_ID,
                            int(body.get("max_new_tokens", 256)),
                        )
                        self._json(200, {"text": text})
                    else:
                        self._json(404, {"error": "not found"})
                except Exception as exc:
                    self._json(500, {"error": str(exc)})

        if __name__ == "__main__":
            ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
    """.trimIndent() + "\n"
}
