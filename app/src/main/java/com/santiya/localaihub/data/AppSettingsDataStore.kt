package com.santiya.localaihub.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.santiya.localaihub.global.AccelerationMode
import com.santiya.localaihub.global.PerformanceMode
import com.santiya.localaihub.hub.ExternalAccessPolicy
import com.santiya.localaihub.hub.LanHubConfig
import com.santiya.localaihub.hub.OrchestraConfig
import com.santiya.localaihub.hub.PreferredModelMap
import com.santiya.localaihub.hub.ThemePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class AppSettingsDataStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private val STREAMING_ENABLED = booleanPreferencesKey("streaming_enabled")
        private val CHAT_MEMORY_ENABLED = booleanPreferencesKey("chat_memory_enabled")
        private val TOOL_CALLING_ENABLED = booleanPreferencesKey("tool_calling_enabled")
        private val TOOL_CALLING_BYPASS_ENABLED = booleanPreferencesKey("tool_calling_bypass_enabled")
        private val IMAGE_BLUR_ENABLED = booleanPreferencesKey("image_blur_enabled")
        private val LOAD_TTS_ON_START = booleanPreferencesKey("load_tts_on_start")
        private val CODE_HIGHLIGHT_ENABLED = booleanPreferencesKey("code_highlight_enabled")
        private val LAST_CHAT_ID = stringPreferencesKey("last_chat_id")
        private val LAST_MODEL_ID = stringPreferencesKey("last_model_id")
        private val ACTIVE_PERSONA_ID = stringPreferencesKey("active_persona_id")
        private val AI_MEMORY_ENABLED = booleanPreferencesKey("ai_memory_enabled")
        private val SECURITY_MODE = stringPreferencesKey("security_mode")
        private val GUIDE_SEEN = booleanPreferencesKey("showcase_seen") // key kept for backward compat
        private val HARDWARE_PROFILE_JSON = stringPreferencesKey("hardware_profile_json")
        private val HARDWARE_TUNING_ENABLED = booleanPreferencesKey("hardware_tuning_enabled")
        private val PERFORMANCE_MODE = stringPreferencesKey("performance_mode")
        private val ACCELERATION_MODE = stringPreferencesKey("acceleration_mode")
        private val ASK_MODEL_RELOAD_DIALOG = booleanPreferencesKey("ask_model_reload_dialog")
        private val THEME_PRESET = stringPreferencesKey("theme_preset")
        private val PREFERRED_MODELS_JSON = stringPreferencesKey("preferred_models_json")
        private val ACTIVE_MODEL_STATE_JSON = stringPreferencesKey("active_model_state_json")
        private val EXTERNAL_ACCESS_POLICY_JSON = stringPreferencesKey("external_access_policy_json")
        private val ORCHESTRA_CONFIG_JSON = stringPreferencesKey("orchestra_config_json")
        private val LAN_HUB_CONFIG_JSON = stringPreferencesKey("lan_hub_config_json")
    }

    val streamingEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[STREAMING_ENABLED] ?: true
    }

    val chatMemoryEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[CHAT_MEMORY_ENABLED] ?: true
    }

    val toolCallingEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[TOOL_CALLING_ENABLED] ?: true
    }

    val toolCallingBypassEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[TOOL_CALLING_BYPASS_ENABLED] ?: false
    }

    val imageBlurEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[IMAGE_BLUR_ENABLED] ?: true
    }

    val loadTTSOnStart: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[LOAD_TTS_ON_START] ?: true
    }

    val codeHighlightEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[CODE_HIGHLIGHT_ENABLED] ?: true
    }

    suspend fun updateStreamingEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[STREAMING_ENABLED] = enabled }
    }

    suspend fun updateChatMemoryEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[CHAT_MEMORY_ENABLED] = enabled }
    }

    suspend fun updateToolCallingEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[TOOL_CALLING_ENABLED] = enabled }
    }

    suspend fun updateToolCallingBypassEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[TOOL_CALLING_BYPASS_ENABLED] = enabled }
    }

    suspend fun updateImageBlurEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[IMAGE_BLUR_ENABLED] = enabled }
    }

    suspend fun updateLoadTTSOnStart(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[LOAD_TTS_ON_START] = enabled }
    }

    suspend fun updateCodeHighlightEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[CODE_HIGHLIGHT_ENABLED] = enabled }
    }

    val lastChatId: Flow<String?> = context.appSettingsDataStore.data.map { prefs ->
        prefs[LAST_CHAT_ID]
    }

    suspend fun saveLastChatId(chatId: String?) {
        context.appSettingsDataStore.edit { prefs ->
            if (chatId != null) {
                prefs[LAST_CHAT_ID] = chatId
            } else {
                prefs.remove(LAST_CHAT_ID)
            }
        }
    }

    val lastModelId: Flow<String?> = context.appSettingsDataStore.data.map { prefs ->
        prefs[LAST_MODEL_ID]
    }

    suspend fun saveLastModelId(modelId: String?) {
        context.appSettingsDataStore.edit { prefs ->
            if (modelId != null) {
                prefs[LAST_MODEL_ID] = modelId
            } else {
                prefs.remove(LAST_MODEL_ID)
            }
        }
    }

    val activePersonaId: Flow<String?> = context.appSettingsDataStore.data.map { prefs ->
        prefs[ACTIVE_PERSONA_ID]
    }

    suspend fun saveActivePersonaId(personaId: String?) {
        context.appSettingsDataStore.edit { prefs ->
            if (personaId != null) {
                prefs[ACTIVE_PERSONA_ID] = personaId
            } else {
                prefs.remove(ACTIVE_PERSONA_ID)
            }
        }
    }

    val aiMemoryEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[AI_MEMORY_ENABLED] ?: true
    }

    suspend fun updateAiMemoryEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[AI_MEMORY_ENABLED] = enabled }
    }

    val securityMode: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[SECURITY_MODE] ?: "REGULAR"
    }

    suspend fun saveSecurityMode(mode: String) {
        context.appSettingsDataStore.edit { it[SECURITY_MODE] = mode }
    }

    val guideSeen: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[GUIDE_SEEN] ?: false
    }

    suspend fun saveGuideSeen(seen: Boolean) {
        context.appSettingsDataStore.edit { it[GUIDE_SEEN] = seen }
    }

    val hardwareProfileJson: Flow<String?> = context.appSettingsDataStore.data.map { prefs ->
        prefs[HARDWARE_PROFILE_JSON]
    }

    val hardwareTuningEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[HARDWARE_TUNING_ENABLED] ?: true
    }

    suspend fun saveHardwareProfile(json: String) {
        context.appSettingsDataStore.edit { it[HARDWARE_PROFILE_JSON] = json }
    }

    suspend fun updateHardwareTuningEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[HARDWARE_TUNING_ENABLED] = enabled }
    }

    val performanceMode: Flow<PerformanceMode> = context.appSettingsDataStore.data.map { prefs ->
        val name = prefs[PERFORMANCE_MODE] ?: PerformanceMode.BALANCED.name
        try { PerformanceMode.valueOf(name) } catch (_: Exception) { PerformanceMode.BALANCED }
    }

    suspend fun savePerformanceMode(mode: PerformanceMode) {
        context.appSettingsDataStore.edit { it[PERFORMANCE_MODE] = mode.name }
    }

    val accelerationMode: Flow<AccelerationMode> = context.appSettingsDataStore.data.map { prefs ->
        val name = prefs[ACCELERATION_MODE] ?: AccelerationMode.AUTO.name
        runCatching { AccelerationMode.valueOf(name) }.getOrDefault(AccelerationMode.AUTO)
    }

    suspend fun saveAccelerationMode(mode: AccelerationMode) {
        context.appSettingsDataStore.edit { it[ACCELERATION_MODE] = mode.name }
    }

    val askModelReloadDialog: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[ASK_MODEL_RELOAD_DIALOG] ?: true
    }

    suspend fun updateAskModelReloadDialog(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[ASK_MODEL_RELOAD_DIALOG] = enabled }
    }

    val themePreset: Flow<ThemePreset> = context.appSettingsDataStore.data.map { prefs ->
        val name = prefs[THEME_PRESET] ?: ThemePreset.OBSIDIAN_MONO.name
        runCatching { ThemePreset.valueOf(name) }.getOrDefault(ThemePreset.OBSIDIAN_MONO)
    }

    suspend fun saveThemePreset(themePreset: ThemePreset) {
        context.appSettingsDataStore.edit { it[THEME_PRESET] = themePreset.name }
    }

    val preferredModels: Flow<PreferredModelMap> = context.appSettingsDataStore.data.map { prefs ->
        decodeJsonOrDefault(prefs[PREFERRED_MODELS_JSON], PreferredModelMap())
    }

    suspend fun savePreferredModels(preferredModels: PreferredModelMap) {
        context.appSettingsDataStore.edit {
            it[PREFERRED_MODELS_JSON] = json.encodeToString(preferredModels)
        }
    }

    suspend fun preferredModelsSnapshot(): PreferredModelMap = preferredModels.first()

    val activeModelState: Flow<ActiveModelState> = context.appSettingsDataStore.data.map { prefs ->
        decodeJsonOrDefault(prefs[ACTIVE_MODEL_STATE_JSON], ActiveModelState())
    }

    suspend fun saveActiveModelState(state: ActiveModelState) {
        context.appSettingsDataStore.edit {
            it[ACTIVE_MODEL_STATE_JSON] = json.encodeToString(state)
        }
    }

    suspend fun activeModelStateSnapshot(): ActiveModelState = activeModelState.first()

    val externalAccessPolicy: Flow<ExternalAccessPolicy> = context.appSettingsDataStore.data.map { prefs ->
        decodeJsonOrDefault(prefs[EXTERNAL_ACCESS_POLICY_JSON], ExternalAccessPolicy())
    }

    suspend fun saveExternalAccessPolicy(policy: ExternalAccessPolicy) {
        context.appSettingsDataStore.edit {
            it[EXTERNAL_ACCESS_POLICY_JSON] = json.encodeToString(policy)
        }
    }

    suspend fun externalAccessPolicySnapshot(): ExternalAccessPolicy = externalAccessPolicy.first()

    val orchestraConfig: Flow<OrchestraConfig> = context.appSettingsDataStore.data.map { prefs ->
        decodeJsonOrDefault(prefs[ORCHESTRA_CONFIG_JSON], OrchestraConfig())
    }

    suspend fun saveOrchestraConfig(config: OrchestraConfig) {
        context.appSettingsDataStore.edit {
            it[ORCHESTRA_CONFIG_JSON] = json.encodeToString(config)
        }
    }

    suspend fun orchestraConfigSnapshot(): OrchestraConfig = orchestraConfig.first()

    val lanHubConfig: Flow<LanHubConfig> = context.appSettingsDataStore.data.map { prefs ->
        decodeJsonOrDefault(prefs[LAN_HUB_CONFIG_JSON], LanHubConfig())
    }

    suspend fun saveLanHubConfig(config: LanHubConfig) {
        context.appSettingsDataStore.edit {
            it[LAN_HUB_CONFIG_JSON] = json.encodeToString(config)
        }
    }

    suspend fun lanHubConfigSnapshot(): LanHubConfig = lanHubConfig.first()

    suspend fun clear() {
        context.appSettingsDataStore.edit { it.clear() }
    }

    private inline fun <reified T> decodeJsonOrDefault(raw: String?, defaultValue: T): T {
        if (raw.isNullOrBlank()) return defaultValue
        return runCatching { json.decodeFromString<T>(raw) }.getOrDefault(defaultValue)
    }
}
