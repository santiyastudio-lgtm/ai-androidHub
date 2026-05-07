package com.santiya.localaihub.storage

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedModelManifestTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun manifest_roundTrips_withoutLosingModelIdentity() {
        val manifest = SharedModelManifest(
            id = "hauhau-gemma-q4kp",
            modelName = "Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-Q4_K_P.gguf",
            providerType = "GGUF",
            fileDisplayName = "Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-Q4_K_P.gguf",
            relativePath = "Download/SantiyaLocalAiHub/models/gguf/",
            fileSize = 1234L
        )

        val encoded = json.encodeToString(SharedModelManifest.serializer(), manifest)
        val decoded = json.decodeFromString(SharedModelManifest.serializer(), encoded)

        assertEquals(manifest, decoded)
    }

    @Test
    fun fallbackModelId_isStableAndNormalized() {
        val id = SharedModelManifest.fallbackModelIdFor("Gemma 4 E2B!!.gguf")

        assertEquals("shared-gemma-4-e2b-.gguf", id)
        assertTrue(id.startsWith("shared-"))
    }
}
