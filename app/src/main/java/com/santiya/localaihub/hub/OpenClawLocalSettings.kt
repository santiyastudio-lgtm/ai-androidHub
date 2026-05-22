package com.santiya.localaihub.hub

import android.content.Context
import com.santiya.localaihub.viewmodel.OpenClawMode

enum class LocalBackendOption {
    GGUF_LOCAL,
    GOOGLE_LOCAL,
    AIRLLM_REMOTE,
    OFFICIAL_GATEWAY,
    TERMUX_LOCAL,
    ORCHESTRA_LAN,
}

data class OpenClawLocalSettings(
    val defaultBackend: LocalBackendOption = LocalBackendOption.GGUF_LOCAL,
    val recommendedModelId: String = OpenClawCatalog.RECOMMENDED_MODEL_ID,
    val autoUseRecommendedModel: Boolean = true,
    val preferMultimodalProjector: Boolean = true,
    val showAdvancedBackends: Boolean = false,
    val enabledByDefault: Boolean = false,
    val preferredOpenClawModelId: String? = null,
    val selectedSkillIds: List<String> = emptyList(),
    val selectedApiToolIds: List<String> = emptyList(),
    val airLlmEndpoint: String = "http://127.0.0.1:8765",
    val airLlmModelId: String = "",
    val officialGatewayEndpoint: String = "http://127.0.0.1:18789",
    val officialGatewayToken: String = "",
    val officialGatewayModelId: String = "openclaw/default",
    val lastSessionEnabled: Boolean = false,
    val lastSessionMode: OpenClawMode = OpenClawMode.NORMAL,
    val lastSessionChatId: String? = null,
    val lastAgentPlan: String? = null,
    val lastAgentSummary: String? = null,
    val lastToolChainStepsJson: String? = null,
    val lastBrowserUrl: String? = null,
    val lastBrowserTitle: String? = null,
    val lastOfflineCityAnswerJson: String? = null,
)

private val DEFAULT_OPENCLAW_SKILLS = listOf(
    "hermes",
    "travel_offline",
    "browser",
    "files",
    "memory",
    "automation",
    "location_control",
    "airllm",
    "official_openclaw_gateway",
)

private val DEFAULT_OPENCLAW_API_TOOLS = listOf(
    "hermes",
    "web_search",
    "browser",
    "api_models",
    "support_logs",
    "system_info",
    "location_control",
    "airllm",
    "official_openclaw_gateway",
)

class OpenClawLocalSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("openclaw_local_settings", Context.MODE_PRIVATE)

    fun read(): OpenClawLocalSettings {
        val backend = runCatching {
            LocalBackendOption.valueOf(
                prefs.getString("defaultBackend", LocalBackendOption.GGUF_LOCAL.name)
                    ?: LocalBackendOption.GGUF_LOCAL.name
            )
        }.getOrDefault(LocalBackendOption.GGUF_LOCAL)
        val lastMode = runCatching {
            OpenClawMode.valueOf(
                prefs.getString("lastSessionMode", OpenClawMode.NORMAL.name)
                    ?: OpenClawMode.NORMAL.name
            )
        }.getOrDefault(OpenClawMode.NORMAL)

        return OpenClawLocalSettings(
            defaultBackend = backend,
            recommendedModelId = prefs.getString("recommendedModelId", OpenClawCatalog.RECOMMENDED_MODEL_ID)
                ?: OpenClawCatalog.RECOMMENDED_MODEL_ID,
            autoUseRecommendedModel = prefs.getBoolean("autoUseRecommendedModel", true),
            preferMultimodalProjector = prefs.getBoolean("preferMultimodalProjector", true),
            showAdvancedBackends = prefs.getBoolean("showAdvancedBackends", false),
            enabledByDefault = prefs.getBoolean("enabledByDefault", false),
            preferredOpenClawModelId = prefs.getString("preferredOpenClawModelId", null),
            selectedSkillIds = prefs.getString("selectedSkillIds", "")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.ifEmpty {
                    if (!prefs.contains("selectedSkillIds")) DEFAULT_OPENCLAW_SKILLS else emptyList()
                }
                .orEmpty(),
            selectedApiToolIds = prefs.getString("selectedApiToolIds", "")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.ifEmpty {
                    if (!prefs.contains("selectedApiToolIds")) DEFAULT_OPENCLAW_API_TOOLS else emptyList()
                }
                .orEmpty(),
            airLlmEndpoint = prefs.getString("airLlmEndpoint", "http://127.0.0.1:8765")
                ?: "http://127.0.0.1:8765",
            airLlmModelId = prefs.getString("airLlmModelId", "") ?: "",
            officialGatewayEndpoint = prefs.getString("officialGatewayEndpoint", "http://127.0.0.1:18789")
                ?: "http://127.0.0.1:18789",
            officialGatewayToken = prefs.getString("officialGatewayToken", "") ?: "",
            officialGatewayModelId = prefs.getString("officialGatewayModelId", "openclaw/default")
                ?: "openclaw/default",
            lastSessionEnabled = prefs.getBoolean("lastSessionEnabled", false),
            lastSessionMode = lastMode,
            lastSessionChatId = prefs.getString("lastSessionChatId", null),
            lastAgentPlan = prefs.getString("lastAgentPlan", null),
            lastAgentSummary = prefs.getString("lastAgentSummary", null),
            lastToolChainStepsJson = prefs.getString("lastToolChainStepsJson", null),
            lastBrowserUrl = prefs.getString("lastBrowserUrl", null),
            lastBrowserTitle = prefs.getString("lastBrowserTitle", null),
            lastOfflineCityAnswerJson = prefs.getString("lastOfflineCityAnswerJson", null),
        )
    }

    fun write(settings: OpenClawLocalSettings) {
        prefs.edit()
            .putString("defaultBackend", settings.defaultBackend.name)
            .putString("recommendedModelId", settings.recommendedModelId)
            .putBoolean("autoUseRecommendedModel", settings.autoUseRecommendedModel)
            .putBoolean("preferMultimodalProjector", settings.preferMultimodalProjector)
            .putBoolean("showAdvancedBackends", settings.showAdvancedBackends)
            .putBoolean("enabledByDefault", settings.enabledByDefault)
            .putString("preferredOpenClawModelId", settings.preferredOpenClawModelId)
            .putString("selectedSkillIds", settings.selectedSkillIds.joinToString(","))
            .putString("selectedApiToolIds", settings.selectedApiToolIds.joinToString(","))
            .putString("airLlmEndpoint", settings.airLlmEndpoint)
            .putString("airLlmModelId", settings.airLlmModelId)
            .putString("officialGatewayEndpoint", settings.officialGatewayEndpoint)
            .putString("officialGatewayToken", settings.officialGatewayToken)
            .putString("officialGatewayModelId", settings.officialGatewayModelId)
            .putBoolean("lastSessionEnabled", settings.lastSessionEnabled)
            .putString("lastSessionMode", settings.lastSessionMode.name)
            .putString("lastSessionChatId", settings.lastSessionChatId)
            .putString("lastAgentPlan", settings.lastAgentPlan)
            .putString("lastAgentSummary", settings.lastAgentSummary)
            .putString("lastToolChainStepsJson", settings.lastToolChainStepsJson)
            .putString("lastBrowserUrl", settings.lastBrowserUrl)
            .putString("lastBrowserTitle", settings.lastBrowserTitle)
            .putString("lastOfflineCityAnswerJson", settings.lastOfflineCityAnswerJson)
            .apply()
    }
}
