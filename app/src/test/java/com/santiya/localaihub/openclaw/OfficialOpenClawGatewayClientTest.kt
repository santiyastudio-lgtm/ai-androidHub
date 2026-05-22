package com.santiya.localaihub.openclaw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialOpenClawGatewayClientTest {

    @Test
    fun `normalizes default local endpoint`() {
        assertEquals(
            "http://127.0.0.1:18789",
            OfficialOpenClawGatewayClient.normalizeLocalEndpoint("127.0.0.1:18789")
        )
    }

    @Test
    fun `allows emulator host endpoint`() {
        assertEquals(
            "http://10.0.2.2:18789",
            OfficialOpenClawGatewayClient.normalizeLocalEndpoint("http://10.0.2.2:18789/")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects public gateway endpoint`() {
        OfficialOpenClawGatewayClient.normalizeLocalEndpoint("https://openclaw.example.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects credentials in endpoint`() {
        OfficialOpenClawGatewayClient.normalizeLocalEndpoint("http://token@127.0.0.1:18789")
    }

    @Test
    fun `builds official operator connect frame`() {
        val frame = OfficialOpenClawGatewayClient.buildOperatorConnectFrame("secret")
        assertTrue(frame.contains("\"method\":\"connect\""))
        assertTrue(frame.contains("operator.read"))
        assertTrue(frame.contains("secret"))
    }

    @Test
    fun `termux bootstrap installs official cli and writes local gateway config`() {
        val script = OfficialOpenClawGatewayTermux.bootstrapScript(port = 18789, token = "secret")
        assertTrue(script.contains("pkg install -y nodejs-lts git"))
        assertTrue(script.contains("npm install -g openclaw@latest"))
        assertTrue(script.contains("\"gateway\":{\"mode\":\"local\""))
        assertTrue(script.contains("OPENCLAW_GATEWAY_TOKEN='secret'"))
    }

    @Test
    fun `termux start binds official gateway to loopback`() {
        val script = OfficialOpenClawGatewayTermux.startScript(port = 18789)
        assertTrue(script.contains("openclaw gateway"))
        assertTrue(script.contains("--bind loopback"))
        assertTrue(script.contains("--allow-unconfigured"))
        assertTrue(script.contains("127.0.0.1:18789"))
    }
}
