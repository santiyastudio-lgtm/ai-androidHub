package com.santiya.localaihub.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRuntimeSettingsTest {

    @Test
    fun `default voice runtime prefers silero local`() {
        val settings = VoiceRuntimeSettings()

        assertEquals(VoiceRuntimeOption.SILERO_LOCAL, settings.selectedRuntime)
        assertTrue(settings.fallbackToLocal)
        assertFalse(settings.preferClonedVoice)
    }

    @Test
    fun `advanced node runtimes stay separate from local runtimes`() {
        val nodeRuntimes = setOf(
            VoiceRuntimeOption.GPT_SOVITS_NODE,
            VoiceRuntimeOption.XTTS_ALLTALK_NODE,
        )

        assertTrue(nodeRuntimes.contains(VoiceRuntimeOption.GPT_SOVITS_NODE))
        assertTrue(nodeRuntimes.contains(VoiceRuntimeOption.XTTS_ALLTALK_NODE))
        assertFalse(nodeRuntimes.contains(VoiceRuntimeOption.SILERO_LOCAL))
        assertFalse(nodeRuntimes.contains(VoiceRuntimeOption.PIPER_LOCAL))
    }
}
