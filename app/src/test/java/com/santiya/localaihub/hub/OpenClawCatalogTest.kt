package com.santiya.localaihub.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenClawCatalogTest {

    @Test
    fun `recommended repo points to exact hauhaucs model repository`() {
        assertEquals(
            "HauhauCS/Gemma-4-E2B-Uncensored-HauhauCS-Aggressive",
            OpenClawCatalog.RECOMMENDED_REPO
        )
    }

    @Test
    fun `recommended model file uses default q4_k_p quant`() {
        assertEquals(
            "Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-Q4_K_P.gguf",
            OpenClawCatalog.RECOMMENDED_MODEL_FILE
        )
    }

    @Test
    fun `recommended projector file keeps mmproj companion mapping`() {
        assertTrue(OpenClawCatalog.RECOMMENDED_PROJECTOR_FILE.startsWith("mmproj-"))
        assertTrue(OpenClawCatalog.RECOMMENDED_PROJECTOR_FILE.endsWith(".gguf"))
    }
}
