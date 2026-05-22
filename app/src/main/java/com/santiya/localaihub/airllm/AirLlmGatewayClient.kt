package com.santiya.localaihub.airllm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AirLlmGatewayStatus(
    val endpoint: String,
    val reachable: Boolean,
    val message: String,
    val modelIds: List<String> = emptyList(),
)

data class AirLlmGenerationResponse(
    val endpoint: String,
    val modelId: String,
    val text: String,
    val rawStatusCode: Int,
)

class AirLlmGatewayClient(
    private val httpClient: OkHttpClient = defaultClient(),
) {
    companion object {
        const val DEFAULT_ENDPOINT = "http://127.0.0.1:8765"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        fun normalizeLocalEndpoint(raw: String): String {
            val value = raw.trim().ifBlank { DEFAULT_ENDPOINT }
            val candidate = if ("://" in value) value else "http://$value"
            val uri = URI(candidate)
            val scheme = uri.scheme?.lowercase(Locale.US)
            require(scheme == "http" || scheme == "https") {
                "AirLLM endpoint must use http or https."
            }
            require(uri.userInfo.isNullOrBlank()) {
                "AirLLM endpoint must not contain embedded credentials."
            }
            require(uri.rawQuery.isNullOrBlank() && uri.rawFragment.isNullOrBlank()) {
                "AirLLM endpoint must be a base URL without query or fragment."
            }

            val host = uri.host?.trim().orEmpty()
            require(isLocalOrPrivateHost(host)) {
                "AirLLM gateway is limited to localhost or private LAN addresses."
            }
            val normalizedHost = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
            val port = if (uri.port >= 0) ":${uri.port}" else ""
            val path = uri.rawPath?.trimEnd('/')?.takeIf { it.isNotBlank() && it != "/" }.orEmpty()
            return "$scheme://$normalizedHost$port$path"
        }

        private fun isLocalOrPrivateHost(host: String): Boolean {
            val h = host.lowercase(Locale.US).trim('[', ']')
            if (h == "localhost" || h == "::1" || h == "127.0.0.1" || h == "10.0.2.2") return true
            if (h.endsWith(".local")) return true
            if (h.startsWith("10.")) return true
            if (h.startsWith("192.168.")) return true
            if (h.startsWith("169.254.")) return true
            val parts = h.split('.').mapNotNull { it.toIntOrNull() }
            return parts.size == 4 && parts[0] == 172 && parts[1] in 16..31
        }
    }

    suspend fun status(rawEndpoint: String): AirLlmGatewayStatus = withContext(Dispatchers.IO) {
        val endpoint = normalizeLocalEndpoint(rawEndpoint)
        val health = getJson("$endpoint/health")
        if (health != null) {
            val models = health.optJSONArray("models")?.toStringList().orEmpty()
            return@withContext AirLlmGatewayStatus(
                endpoint = endpoint,
                reachable = true,
                message = health.optString("message").ifBlank { "AirLLM gateway is reachable." },
                modelIds = models,
            )
        }

        val modelsJson = getJson("$endpoint/v1/models")
        if (modelsJson != null) {
            val models = modelsJson.optJSONArray("data")?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index)
                        val id = item?.optString("id").orEmpty()
                        if (id.isNotBlank()) add(id)
                    }
                }
            }.orEmpty()
            return@withContext AirLlmGatewayStatus(
                endpoint = endpoint,
                reachable = true,
                message = "OpenAI-compatible AirLLM gateway is reachable.",
                modelIds = models,
            )
        }

        AirLlmGatewayStatus(
            endpoint = endpoint,
            reachable = false,
            message = "AirLLM gateway is not reachable. Start the Python gateway in Termux, proot, desktop, or LAN and keep this endpoint local/private.",
        )
    }

    suspend fun generate(
        rawEndpoint: String,
        modelId: String,
        prompt: String,
        systemPrompt: String = "",
        maxNewTokens: Int = 256,
    ): AirLlmGenerationResponse = withContext(Dispatchers.IO) {
        val endpoint = normalizeLocalEndpoint(rawEndpoint)
        val safeModelId = modelId.trim().ifBlank { "default" }
        val messages = JSONArray().apply {
            if (systemPrompt.isNotBlank()) {
                put(JSONObject().put("role", "system").put("content", systemPrompt))
            }
            put(JSONObject().put("role", "user").put("content", prompt))
        }
        val openAiBody = JSONObject()
            .put("model", safeModelId)
            .put("messages", messages)
            .put("max_tokens", maxNewTokens.coerceIn(1, 4096))
            .put("temperature", 0.7)

        val openAi = postJson("$endpoint/v1/chat/completions", openAiBody)
        if (openAi != null && openAi.code in 200..299) {
            return@withContext AirLlmGenerationResponse(
                endpoint = endpoint,
                modelId = safeModelId,
                text = parseOpenAiChatText(openAi.body).ifBlank { openAi.body.toString() },
                rawStatusCode = openAi.code,
            )
        }

        val fallbackBody = JSONObject()
            .put("model", safeModelId)
            .put("prompt", prompt)
            .put("max_new_tokens", maxNewTokens.coerceIn(1, 4096))
        val fallback = postJson("$endpoint/generate", fallbackBody)
            ?: error("AirLLM gateway did not respond.")
        if (fallback.code !in 200..299) {
            error("AirLLM gateway returned HTTP ${fallback.code}: ${fallback.body.optString("error", fallback.body.toString())}")
        }
        AirLlmGenerationResponse(
            endpoint = endpoint,
            modelId = safeModelId,
            text = fallback.body.optString("text")
                .ifBlank { fallback.body.optString("response") }
                .ifBlank { fallback.body.optString("output") }
                .ifBlank { fallback.body.toString() },
            rawStatusCode = fallback.code,
        )
    }

    private fun getJson(url: String): JSONObject? {
        val request = Request.Builder().url(url).get().build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                JSONObject(response.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    private fun postJson(url: String, body: JSONObject): HttpJsonResponse? {
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrElse {
                    JSONObject().put("text", text)
                }
                HttpJsonResponse(response.code, json)
            }
        }.getOrNull()
    }

    private fun parseOpenAiChatText(json: JSONObject): String {
        val choices = json.optJSONArray("choices") ?: return ""
        val first = choices.optJSONObject(0) ?: return ""
        val message = first.optJSONObject("message")
        return message?.optString("content").orEmpty()
            .ifBlank { first.optString("text") }
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (index in 0 until length()) {
            val value = optString(index).trim()
            if (value.isNotBlank()) add(value)
        }
    }

    private data class HttpJsonResponse(
        val code: Int,
        val body: JSONObject,
    )
}
