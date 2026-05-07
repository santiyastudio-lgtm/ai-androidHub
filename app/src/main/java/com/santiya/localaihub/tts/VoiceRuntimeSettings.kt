package com.santiya.localaihub.tts

import android.content.Context

enum class VoiceRuntimeOption {
    SILERO_LOCAL,
    PIPER_LOCAL,
    GPT_SOVITS_NODE,
    XTTS_ALLTALK_NODE,
}

data class VoiceNodeConfig(
    val url: String = "",
)

data class VoiceRuntimeSettings(
    val selectedRuntime: VoiceRuntimeOption = VoiceRuntimeOption.SILERO_LOCAL,
    val fallbackToLocal: Boolean = true,
    val preferClonedVoice: Boolean = false,
    val gptSovitsNode: VoiceNodeConfig = VoiceNodeConfig(),
    val xttsAllTalkNode: VoiceNodeConfig = VoiceNodeConfig(),
)

class VoiceRuntimeSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("voice_runtime_settings", Context.MODE_PRIVATE)

    fun read(): VoiceRuntimeSettings {
        val selectedRuntime = runCatching {
            VoiceRuntimeOption.valueOf(
                prefs.getString("selectedRuntime", VoiceRuntimeOption.SILERO_LOCAL.name)
                    ?: VoiceRuntimeOption.SILERO_LOCAL.name
            )
        }.getOrDefault(VoiceRuntimeOption.SILERO_LOCAL)

        return VoiceRuntimeSettings(
            selectedRuntime = selectedRuntime,
            fallbackToLocal = prefs.getBoolean("fallbackToLocal", true),
            preferClonedVoice = prefs.getBoolean("preferClonedVoice", false),
            gptSovitsNode = VoiceNodeConfig(prefs.getString("gptSovitsUrl", "") ?: ""),
            xttsAllTalkNode = VoiceNodeConfig(prefs.getString("xttsAllTalkUrl", "") ?: ""),
        )
    }

    fun write(settings: VoiceRuntimeSettings) {
        prefs.edit()
            .putString("selectedRuntime", settings.selectedRuntime.name)
            .putBoolean("fallbackToLocal", settings.fallbackToLocal)
            .putBoolean("preferClonedVoice", settings.preferClonedVoice)
            .putString("gptSovitsUrl", settings.gptSovitsNode.url)
            .putString("xttsAllTalkUrl", settings.xttsAllTalkNode.url)
            .apply()
    }
}
