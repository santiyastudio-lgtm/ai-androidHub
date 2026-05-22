package com.santiya.localaihub.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserUrlPolicyTest {

    @Test
    fun `adds https scheme for host-only input`() {
        assertEquals("https://example.com", BrowserUrlPolicy.normalize("example.com"))
    }

    @Test
    fun `keeps valid https url`() {
        assertEquals(
            "https://example.com/path?q=ai",
            BrowserUrlPolicy.normalize("https://example.com/path?q=ai")
        )
    }

    @Test
    fun `rejects script and file schemes`() {
        assertNull(BrowserUrlPolicy.normalizeOrNull("javascript:alert(1)"))
        assertNull(BrowserUrlPolicy.normalizeOrNull("file:///sdcard/secret.txt"))
        assertNull(BrowserUrlPolicy.normalizeOrNull("content://downloads/item/1"))
    }

    @Test
    fun `rejects urls with embedded credentials`() {
        assertNull(BrowserUrlPolicy.normalizeOrNull("https://user:pass@example.com"))
    }
}
