package com.santiya.localaihub.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.santiya.localaihub.R

@OptIn(ExperimentalTextApi::class)
val ManropeFontFamily = FontFamily(
    Font(
        resId = R.font.manrope,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.Normal.weight)
        )
    )
)

@OptIn(ExperimentalTextApi::class)
val MapleMonoFontFamily = FontFamily(
    Font(
        resId = R.font.maple_mono,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.Normal.weight)
        )
    )
)

// Legacy alias вЂ” used throughout the codebase
val maple = MapleMonoFontFamily