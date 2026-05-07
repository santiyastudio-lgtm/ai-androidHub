package com.santiya.localaihub.global

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalizedTextTest {

    @Test
    fun localizedText_repairsRussianMojibake() {
        val value = localizedText(
            AppLanguage.RUSSIAN,
            "РћС‚РїСЂР°РІРёС‚СЊ Р»РѕРіРё РІ РїРѕРґРґРµСЂР¶РєСѓ",
            "Send logs to support"
        )

        assertEquals("Отправить логи в поддержку", value)
    }

    @Test
    fun localizedText_keepsEnglishUntouched() {
        val value = localizedText(
            AppLanguage.ENGLISH,
            "РћС‚РїСЂР°РІРёС‚СЊ Р»РѕРіРё РІ РїРѕРґРґРµСЂР¶РєСѓ",
            "Send logs to support"
        )

        assertEquals("Send logs to support", value)
    }
}
