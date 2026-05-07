package com.santiya.localaihub.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportReportManagerTest {

    @Test
    fun telegramStartPayload_normalizesTicketId() {
        val payload = SupportReportManager.telegramStartPayload("20260505-120000-AbCd-1234")
        assertEquals("support_20260505120000abcd1234", payload)
    }

    @Test
    fun commentPreview_trimsWhitespaceAndShortensLongText() {
        val preview = SupportReportManager.commentPreview("  line1   line2   line3  ", maxLength = 12)
        assertEquals("line1 lin...", preview)
    }

    @Test
    fun buildShareText_containsTicketAndBot() {
        val text = SupportReportManager.buildShareText("ticket-1", "abc123", "hello")
        assertTrue(text.contains("ticket-1"))
        assertTrue(text.contains("@SantiyaSupportBot"))
        assertTrue(text.contains("abc123"))
    }
}
