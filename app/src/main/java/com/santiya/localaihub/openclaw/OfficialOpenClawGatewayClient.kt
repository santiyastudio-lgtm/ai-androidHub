package com.santiya.localaihub.openclaw

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class OfficialOpenClawGatewayStatus(
    val endpoint: String,
    val reachable: Boolean,
    val message: String,
    val modelIds: List<String> = emptyList(),
)

data class OfficialOpenClawGatewayChatResponse(
    val endpoint: String,
    val modelId: String,
    val text: String,
    val rawStatusCode: Int,
)

class OfficialOpenClawGatewayClient(
    private val httpClient: OkHttpClient = defaultClient(),
) {
    companion object {
        const val DEFAULT_ENDPOINT = "http://127.0.0.1:18789"
        const val DEFAULT_MODEL_ID = "openclaw/default"
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
                "OpenClaw Gateway endpoint must use http or https."
            }
            require(uri.userInfo.isNullOrBlank()) {
                "OpenClaw Gateway endpoint must not contain embedded credentials."
            }
            require(uri.rawQuery.isNullOrBlank() && uri.rawFragment.isNullOrBlank()) {
                "OpenClaw Gateway endpoint must be a base URL without query or fragment."
            }

            val host = uri.host?.trim().orEmpty()
            require(isLocalOrPrivateHost(host)) {
                "OpenClaw Gateway is limited to localhost or private LAN addresses."
            }
            val normalizedHost = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
            val port = if (uri.port >= 0) ":${uri.port}" else ""
            val path = uri.rawPath?.trimEnd('/')?.takeIf { it.isNotBlank() && it != "/" }.orEmpty()
            return "$scheme://$normalizedHost$port$path"
        }

        fun toWebSocketUrl(raw: String): String {
            val endpoint = normalizeLocalEndpoint(raw)
            return when {
                endpoint.startsWith("https://") -> "wss://" + endpoint.removePrefix("https://")
                else -> "ws://" + endpoint.removePrefix("http://")
            }
        }

        fun buildOperatorConnectFrame(token: String = ""): String {
            val auth = if (token.isBlank()) "{}" else """{"token":"${escapeJson(token)}"}"""
            return """
                {"type":"req","id":"connect-${UUID.randomUUID()}","method":"connect","params":{"minProtocol":3,"maxProtocol":3,"client":{"id":"santiya-localaihub-android","version":"1","platform":"android","mode":"operator"},"role":"operator","scopes":["operator.read","operator.write"],"caps":[],"commands":[],"permissions":{},"auth":$auth,"locale":"${escapeJson(Locale.getDefault().toLanguageTag())}","userAgent":"SantiyaLocalAiHub/OpenClawGateway"}}
            """.trimIndent()
        }

        private fun escapeJson(value: String): String = buildString {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
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

    suspend fun status(rawEndpoint: String, token: String = ""): OfficialOpenClawGatewayStatus = withContext(Dispatchers.IO) {
        val endpoint = normalizeLocalEndpoint(rawEndpoint)
        val models = getJson("$endpoint/v1/models", token)
        if (models != null) {
            val ids = models.optJSONArray("data")?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val id = array.optJSONObject(index)?.optString("id").orEmpty()
                        if (id.isNotBlank()) add(id)
                    }
                }
            }.orEmpty()
            return@withContext OfficialOpenClawGatewayStatus(
                endpoint = endpoint,
                reachable = true,
                message = "Official OpenClaw Gateway is reachable.",
                modelIds = ids,
            )
        }
        val status = getJson("$endpoint/status", token)
            ?: getJson("$endpoint/health", token)
            ?: getJson("$endpoint/healthz", token)
            ?: getJson("$endpoint/readyz", token)
        if (status != null) {
            return@withContext OfficialOpenClawGatewayStatus(
                endpoint = endpoint,
                reachable = true,
                message = status.optString("status").ifBlank {
                    status.optString("message", "Official OpenClaw Gateway is reachable.")
                },
            )
        }
        webSocketProbe(endpoint, token)?.let { message ->
            return@withContext OfficialOpenClawGatewayStatus(
                endpoint = endpoint,
                reachable = true,
                message = message,
            )
        }
        OfficialOpenClawGatewayStatus(
            endpoint = endpoint,
            reachable = false,
            message = "Official OpenClaw Gateway is not reachable. Start `openclaw gateway --bind loopback --port 18789 --allow-unconfigured` or use Termux Local bootstrap, then keep the endpoint local/private.",
        )
    }

    suspend fun chatCompletion(
        rawEndpoint: String,
        token: String = "",
        modelId: String = DEFAULT_MODEL_ID,
        messages: List<JSONObject>,
        maxTokens: Int = 2048,
    ): OfficialOpenClawGatewayChatResponse = withContext(Dispatchers.IO) {
        val endpoint = normalizeLocalEndpoint(rawEndpoint)
        val model = modelId.ifBlank { DEFAULT_MODEL_ID }
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray(messages))
            put("max_tokens", maxTokens)
            put("stream", false)
        }
        val request = Request.Builder()
            .url("$endpoint/v1/chat/completions")
            .applyAuth(token)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Official OpenClaw Gateway returned HTTP ${response.code}: ${bodyText.ifBlank { response.message }}")
            }
            val json = JSONObject(bodyText.ifBlank { "{}" })
            val text = json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .ifBlank { json.optString("output_text") }
                .ifBlank { json.optString("text") }
            OfficialOpenClawGatewayChatResponse(
                endpoint = endpoint,
                modelId = model,
                text = text,
                rawStatusCode = response.code,
            )
        }
    }

    suspend fun invokeTool(
        rawEndpoint: String,
        token: String = "",
        toolName: String,
        arguments: JSONObject = JSONObject(),
    ): JSONObject = withContext(Dispatchers.IO) {
        val endpoint = normalizeLocalEndpoint(rawEndpoint)
        val payload = JSONObject().apply {
            put("name", toolName)
            put("arguments", arguments)
        }
        val request = Request.Builder()
            .url("$endpoint/tools/invoke")
            .applyAuth(token)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Official OpenClaw tools/invoke returned HTTP ${response.code}: ${bodyText.ifBlank { response.message }}")
            }
            JSONObject(bodyText.ifBlank { "{}" })
        }
    }

    private fun Request.Builder.applyAuth(token: String): Request.Builder = apply {
        if (token.isNotBlank()) {
            header("Authorization", "Bearer $token")
            header("x-openclaw-gateway-token", token)
        }
    }

    private fun getJson(url: String, token: String): JSONObject? = runCatching {
        val request = Request.Builder().url(url).applyAuth(token).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
        }
    }.getOrNull()

    private fun webSocketProbe(endpoint: String, token: String): String? = runCatching {
        val latch = CountDownLatch(1)
        val opened = AtomicBoolean(false)
        val messageRef = AtomicReference<String?>(null)
        val failureRef = AtomicReference<String?>(null)
        val request = Request.Builder()
            .url(toWebSocketUrl(endpoint))
            .applyAuth(token)
            .build()

        val socket = httpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened.set(true)
                    webSocket.send(buildOperatorConnectFrame(token))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    messageRef.set(text.take(240))
                    webSocket.close(1000, "probe complete")
                    latch.countDown()
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (opened.get()) {
                        messageRef.compareAndSet(null, "WebSocket opened, then closing: $code ${reason.take(120)}")
                    }
                    latch.countDown()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (opened.get()) {
                        messageRef.compareAndSet(null, "WebSocket opened and closed: $code ${reason.take(120)}")
                    }
                    latch.countDown()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    failureRef.set(t.message ?: response?.message)
                    latch.countDown()
                }
            }
        )

        latch.await(3, TimeUnit.SECONDS)
        val message = messageRef.get()
        when {
            opened.get() && !message.isNullOrBlank() ->
                "Official OpenClaw Gateway WebSocket is reachable. Probe response: $message"
            opened.get() ->
                "Official OpenClaw Gateway WebSocket is reachable."
            else -> {
                socket.cancel()
                null
            }
        }
    }.getOrNull()
}
