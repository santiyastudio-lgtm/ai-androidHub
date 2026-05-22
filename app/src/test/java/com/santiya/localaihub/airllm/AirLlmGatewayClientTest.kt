package com.santiya.localaihub.airllm

import org.junit.Assert.assertEquals
import org.junit.Test

class AirLlmGatewayClientTest {

    @Test
    fun `normalizes localhost endpoint without scheme`() {
        assertEquals(
            "http://127.0.0.1:8765",
            AirLlmGatewayClient.normalizeLocalEndpoint("127.0.0.1:8765")
        )
    }

    @Test
    fun `allows emulator desktop gateway`() {
        assertEquals(
            "http://10.0.2.2:8765",
            AirLlmGatewayClient.normalizeLocalEndpoint("http://10.0.2.2:8765/")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects public gateway host`() {
        AirLlmGatewayClient.normalizeLocalEndpoint("https://example.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects embedded credentials`() {
        AirLlmGatewayClient.normalizeLocalEndpoint("http://user:pass@127.0.0.1:8765")
    }
}
