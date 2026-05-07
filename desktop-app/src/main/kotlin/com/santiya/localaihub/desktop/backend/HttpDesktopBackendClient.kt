package com.santiya.localaihub.desktop.backend

import com.santiya.localaihub.desktop.state.CatalogEntry
import com.santiya.localaihub.desktop.state.CatalogWarning
import com.santiya.localaihub.desktop.state.ComposerMode
import com.santiya.localaihub.desktop.state.ExternalAccessPolicy
import com.santiya.localaihub.desktop.state.LanNodeCardState
import com.santiya.localaihub.desktop.state.ModelSummary
import com.santiya.localaihub.desktop.state.OrchestraConfig
import com.santiya.localaihub.desktop.state.PluginSummary
import com.santiya.localaihub.desktop.state.PreferredModels
import com.santiya.localaihub.desktop.state.RuntimeBadgeState
import com.santiya.localaihub.desktop.state.SetupRecommendation
import com.santiya.localaihub.desktop.state.StatusSummary
import com.santiya.localaihub.desktop.state.ToolDefinition
import com.santiya.localaihub.desktop.state.ToolState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val json = Json {
    ignoreUnknownKeys = true
}

abstract class HttpDesktopBackendClient(
    override val baseUrl: String
) : DesktopBackendClient {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    override suspend fun ping(): Boolean {
        return try {
            getJson("/health")
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun status(): StatusSummary {
        val root = getJson("/api/status").jsonObject
        return StatusSummary(
            appName = root.string("app").ifBlank { "SantiyaLocalAiHub" },
            version = root.string("version"),
            pairingTokenConfigured = root.boolean("pairingTokenConfigured"),
            modelCount = root.int("modelCount"),
            runtimeCount = root.int("runtimeCount"),
            pluginCount = root.int("pluginCount"),
            toolCount = root.int("toolCount"),
            ragCount = root.int("ragCount"),
            lanEnabled = root.boolean("lanEnabled"),
            orchestraEnabled = root.boolean("orchestraEnabled"),
            toolCallingEnabled = root.boolean("toolCallingEnabled")
        )
    }

    override suspend fun models(): List<ModelSummary> {
        return getArray("/api/models").map { model ->
            val item = model.jsonObject
            ModelSummary(
                id = item.string("id"),
                name = item.string("name"),
                path = item.string("path"),
                sizeMb = item.int("sizeMb").takeIf { it > 0 } ?: item.int("size_mb"),
                runtime = item.string("runtime"),
                modelType = item.string("modelType").ifBlank { item.string("model_type") },
                capabilities = item.stringList("capabilities")
            )
        }
    }

    override suspend fun catalog(): List<CatalogEntry> {
        return getArray("/api/catalog").map { entry ->
            val item = entry.jsonObject
            CatalogEntry(
                id = item.string("id"),
                title = item.string("titleRu").ifBlank { item.string("title_ru") },
                description = item.string("descriptionRu").ifBlank { item.string("description_ru") },
                taskLabel = item.string("taskLabelRu").ifBlank { item.string("task_label_ru") },
                thumbnailUrl = item.string("thumbnailUrl").ifBlank { item.string("thumbnail_url") },
                previewImages = item.stringList("previewImages").ifEmpty { item.stringList("preview_images") },
                ramEstimateMb = item.int("ramEstimateMb").takeIf { it > 0 } ?: item.int("ram_estimate_mb").takeIf { it > 0 },
                supportStatus = item.string("supportStatus").ifBlank { item.string("support_status") },
                downloadability = item.string("downloadability"),
                warnings = item.array("warnings").map { warning ->
                    val payload = warning.jsonObject
                    CatalogWarning(
                        title = payload.string("title"),
                        message = payload.string("message"),
                        severity = payload.string("severity")
                    )
                },
                sourceLabel = item.string("sourceLabel").ifBlank { item.string("source_label") },
                assistantEligible = item.boolean("assistantEligible") || item.boolean("assistant_eligible"),
                liveEligible = item.boolean("liveEligible") || item.boolean("live_eligible"),
                tags = item.stringList("tagsRu").ifEmpty { item.stringList("tags_ru") }
            )
        }
    }

    override suspend fun runtimes(): List<RuntimeBadgeState> {
        return getArray("/api/runtimes").map { runtime ->
            val item = runtime.jsonObject
            RuntimeBadgeState(
                name = item.string("name").ifBlank { item.string("capability") },
                status = item.string("status").ifBlank { item.string("runtimeState") },
                summary = item.string("summary").ifBlank {
                    listOf(item.string("provider"), item.string("notes")).filter { it.isNotBlank() }.joinToString(" • ")
                }
            )
        }
    }

    override suspend fun plugins(): List<PluginSummary> {
        return getArray("/api/plugins").map { plugin ->
            val item = plugin.jsonObject
            PluginSummary(
                name = item.string("name"),
                description = item.string("description"),
                version = item.string("version"),
                enabled = item.boolean("enabled")
            )
        }
    }

    override suspend fun tools(): List<ToolDefinition> {
        return getArray("/api/tools").map { tool ->
            val item = tool.jsonObject
            ToolDefinition(
                pluginName = item.string("pluginName").ifBlank { item.string("plugin") },
                toolName = item.string("name").ifBlank { item.string("toolName") },
                description = item.string("description"),
                parameters = item.array("parameters").map { param ->
                    val payload = param.jsonObject
                    buildString {
                        append(payload.string("name"))
                        val type = payload.string("type")
                        if (type.isNotBlank()) {
                            append(": ")
                            append(type)
                        }
                    }
                }
            )
        }
    }

    override suspend fun toolState(): ToolState {
        val item = getJson("/api/tool-state").jsonObject
        return ToolState(
            enabledPlugins = item.stringList("enabledPlugins").toSet(),
            webSearchEnabled = item.boolean("webSearchEnabled"),
            grammarMode = item.boolean("grammarMode"),
            multiTurn = item.boolean("multiTurn"),
            modelSupportGating = item.boolean("modelSupportGating"),
            bypass = item.boolean("bypass")
        )
    }

    override suspend fun updateToolState(state: ToolState) {
        putJson(
            "/api/tool-state",
            buildJsonObject {
                put("enabledPlugins", JsonArray(state.enabledPlugins.map(::JsonPrimitive)))
                put("webSearchEnabled", JsonPrimitive(state.webSearchEnabled))
                put("grammarMode", JsonPrimitive(state.grammarMode))
                put("multiTurn", JsonPrimitive(state.multiTurn))
                put("modelSupportGating", JsonPrimitive(state.modelSupportGating))
                put("bypass", JsonPrimitive(state.bypass))
            }
        )
    }

    override suspend fun preferredModels(): PreferredModels {
        val item = getJson("/api/preferred-models").jsonObject
        return PreferredModels(
            chatModelId = item.string("chatModelId"),
            visionModelId = item.string("visionModelId"),
            imageGenerationModelId = item.string("imageGenerationModelId"),
            ttsModelId = item.string("ttsModelId"),
            assistantLiveModelId = item.string("assistantLiveModelId"),
            filesModelId = item.string("filesModelId")
        )
    }

    override suspend fun updatePreferredModels(models: PreferredModels) {
        putJson(
            "/api/preferred-models",
            buildJsonObject {
                put("chatModelId", JsonPrimitive(models.chatModelId))
                put("visionModelId", JsonPrimitive(models.visionModelId))
                put("imageGenerationModelId", JsonPrimitive(models.imageGenerationModelId))
                put("ttsModelId", JsonPrimitive(models.ttsModelId))
                put("assistantLiveModelId", JsonPrimitive(models.assistantLiveModelId))
                put("filesModelId", JsonPrimitive(models.filesModelId))
            }
        )
    }

    override suspend fun externalAccess(): ExternalAccessPolicy {
        val item = getJson("/api/external-access").jsonObject
        return ExternalAccessPolicy(
            enabled = item.boolean("enabled"),
            requireApproval = item.boolean("requireApproval"),
            description = item.string("description")
        )
    }

    override suspend fun updateExternalAccess(policy: ExternalAccessPolicy) {
        putJson(
            "/api/external-access",
            buildJsonObject {
                put("enabled", JsonPrimitive(policy.enabled))
                put("requireApproval", JsonPrimitive(policy.requireApproval))
                put("description", JsonPrimitive(policy.description))
            }
        )
    }

    override suspend fun orchestra(): OrchestraConfig {
        val item = getJson("/api/orchestra").jsonObject
        return OrchestraConfig(
            enabled = item.boolean("enabled"),
            autoAssign = item.boolean("autoAssign"),
            allowLanSpillover = item.boolean("allowLanSpillover")
        )
    }

    override suspend fun updateOrchestra(config: OrchestraConfig) {
        putJson(
            "/api/orchestra",
            buildJsonObject {
                put("enabled", JsonPrimitive(config.enabled))
                put("autoAssign", JsonPrimitive(config.autoAssign))
                put("allowLanSpillover", JsonPrimitive(config.allowLanSpillover))
            }
        )
    }

    override suspend fun nodes(): List<LanNodeCardState> {
        return getArray("/api/lan/nodes", "/api/nodes").map { node ->
            val item = node.jsonObject
            LanNodeCardState(
                id = item.string("id"),
                name = item.string("name"),
                host = item.string("host"),
                port = item.int("port"),
                status = item.string("status"),
                platform = item.string("platform"),
                notes = item.string("notes"),
                capabilities = item.stringList("capabilities"),
                installedModelCount = item.int("installedModelCount").takeIf { it > 0 } ?: item.int("installed_model_count"),
                isLocal = item.boolean("isLocal") || item.boolean("is_local"),
                transportSummary = item.stringList("transportProtocols").ifEmpty { item.stringList("transport_protocols") }.joinToString(" • ")
            )
        }
    }

    override suspend fun setupRecommendation(): SetupRecommendation? {
        return try {
            val item = getJson("/api/setup/recommendation").jsonObject
            SetupRecommendation(
                title = item.string("title"),
                description = item.string("description"),
                sizeLabel = item.string("sizeLabel"),
                installActionLabel = item.string("installActionLabel"),
                projectorTitle = item.nullableString("projectorTitle"),
                projectorDescription = item.nullableString("projectorDescription"),
                projectorActionLabel = item.nullableString("projectorActionLabel"),
                warning = item.nullableString("warning"),
                status = item.string("status")
            )
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun chatGenerate(prompt: String, systemPrompt: String, mode: ComposerMode, modelId: String): String {
        val response = postJson(
            "/api/chat/generate",
            buildJsonObject {
                put("prompt", JsonPrimitive(prompt))
                put("systemPrompt", JsonPrimitive(systemPrompt))
                put("mode", JsonPrimitive(mode.name.lowercase()))
                put("modelId", JsonPrimitive(modelId))
            }
        )
        return renderResponseText(response)
    }

    override suspend fun lanExecute(prompt: String, hostHint: String, remotePort: Int, systemPrompt: String, mode: ComposerMode): String {
        val response = postJson(
            "/api/lan/execute",
            buildJsonObject {
                put("prompt", JsonPrimitive(prompt))
                put("host", JsonPrimitive(hostHint))
                put("remotePort", JsonPrimitive(remotePort))
                put("systemPrompt", JsonPrimitive(systemPrompt))
                put("mode", JsonPrimitive(mode.name.lowercase()))
            }
        )
        return renderResponseText(response)
    }

    protected suspend fun getJson(path: String): JsonElement = request("GET", path, null)
    protected suspend fun getJson(primaryPath: String, fallbackPath: String): JsonElement {
        return try {
            request("GET", primaryPath, null)
        } catch (error: BackendUnavailableException) {
            if (error.message?.contains("HTTP 404") == true) {
                request("GET", fallbackPath, null)
            } else {
                throw error
            }
        }
    }
    protected suspend fun putJson(path: String, body: JsonObject) {
        request("PUT", path, body)
    }
    protected suspend fun postJson(path: String, body: JsonObject): JsonElement = request("POST", path, body)
    protected suspend fun getArray(path: String): List<JsonElement> = getJson(path).jsonArray.toList()
    protected suspend fun getArray(primaryPath: String, fallbackPath: String): List<JsonElement> =
        getJson(primaryPath, fallbackPath).jsonArray.toList()

    private suspend fun request(method: String, path: String, body: JsonObject?): JsonElement = withContext(Dispatchers.IO) {
        val requestBuilder = HttpRequest.newBuilder(URI.create(baseUrl.trimEnd('/') + path))
            .timeout(Duration.ofSeconds(8))
            .header("Accept", "application/json")

        if (body != null) {
            requestBuilder.header("Content-Type", "application/json; charset=utf-8")
            requestBuilder.method(method, HttpRequest.BodyPublishers.ofString(body.toString()))
        } else {
            requestBuilder.method(method, HttpRequest.BodyPublishers.noBody())
        }

        val response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
        val payload = response.body().ifBlank { "{}" }
        if (response.statusCode() >= 400) {
            throw BackendUnavailableException("HTTP ${response.statusCode()} from $baseUrl$path: $payload")
        }
        json.parseToJsonElement(payload)
    }

    private fun renderResponseText(response: JsonElement): String {
        val objectBody = response as? JsonObject ?: return response.toString()
        return objectBody.nullableString("output")
            ?: objectBody.nullableString("result")
            ?: objectBody.nullableString("message")
            ?: response.toString()
    }
}

class CoreBackendClient(baseUrl: String) : HttpDesktopBackendClient(baseUrl)
class HubBackendClient(baseUrl: String) : HttpDesktopBackendClient(baseUrl)

private fun JsonObject.string(name: String): String =
    this[name]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.nullableString(name: String): String? {
    val value = this[name] ?: return null
    if (value == JsonNull) return null
    return value.jsonPrimitive.contentOrNull
}

private fun JsonObject.boolean(name: String): Boolean =
    this[name]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false

private fun JsonObject.int(name: String): Int =
    this[name]?.jsonPrimitive?.intOrNull ?: 0

private fun JsonObject.array(name: String): List<JsonElement> =
    (this[name] as? JsonArray)?.toList().orEmpty()

private fun JsonObject.stringList(name: String): List<String> =
    array(name).mapNotNull { it.jsonPrimitive.contentOrNull }
