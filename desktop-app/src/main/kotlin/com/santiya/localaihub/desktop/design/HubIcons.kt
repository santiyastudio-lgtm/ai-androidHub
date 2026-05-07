package com.santiya.localaihub.desktop.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object HubIcons {
    val ArrowLeft by lazy { tabler("M5 12l14 0", "M5 12l6 6", "M5 12l6 -6") }
    val ChevronDown by lazy { tabler("M6 9l6 6l6 -6") }
    val Menu by lazy { tabler("M4 8l16 0", "M4 16l16 0") }
    val Check by lazy { tabler("M5 12l5 5l10 -10") }
    val Refresh by lazy { tabler("M20 11a8.1 8.1 0 0 0 -15.5 -2m-.5 -4v4h4", "M4 13a8.1 8.1 0 0 0 15.5 2m.5 4v-4h-4") }
    val Adjustments by lazy { tabler("M4 10a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M6 4v4", "M6 12v8", "M10 16a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M12 4v10", "M12 18v2", "M16 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M18 4v1", "M18 9v11") }
    val Sparkles by lazy { tabler("M16 18a2 2 0 0 1 2 2a2 2 0 0 1 2 -2a2 2 0 0 1 -2 -2a2 2 0 0 1 -2 2", "M3 12a6 6 0 0 1 6 6a6 6 0 0 1 6 -6a6 6 0 0 1 -6 -6a6 6 0 0 1 -6 6", "M16 6a2 2 0 0 1 2 2a2 2 0 0 1 2 -2a2 2 0 0 1 -2 -2a2 2 0 0 1 -2 2") }
    val Volume by lazy { tabler("M15 8a5 5 0 0 1 0 8", "M17.7 5a9 9 0 0 1 0 14", "M6 15h-2a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1h2l3.5 -4.5a.8 .8 0 0 1 1.5 .5v14a.8 .8 0 0 1 -1.5 .5l-3.5 -4.5") }
    val Photo by lazy { tabler("M15 8h.01", "M3 6a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v12a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-12z", "M3 16l5 -5c.928 -.893 2.072 -.893 3 0l5 5", "M14 14l1 -1c.928 -.893 2.072 -.893 3 0l3 3") }
    val Bolt by lazy { tabler("M13 3l0 7l6 0l-8 11l0 -7l-6 0l8 -11") }
    val Settings by lazy { tabler("M10.325 4.317c.426 -1.756 2.924 -1.756 3.35 0a1.724 1.724 0 0 0 2.573 1.066c1.543 -.94 3.31 .826 2.37 2.37a1.724 1.724 0 0 0 1.065 2.572c1.756 .426 1.756 2.924 0 3.35a1.724 1.724 0 0 0 -1.066 2.573c.94 1.543 -.826 3.31 -2.37 2.37a1.724 1.724 0 0 0 -2.572 1.065c-.426 1.756 -2.924 1.756 -3.35 0a1.724 1.724 0 0 0 -2.573 -1.066c-1.543 .94 -3.31 -.826 -2.37 -2.37a1.724 1.724 0 0 0 -1.065 -2.572c-1.756 -.426 -1.756 -2.924 0 -3.35a1.724 1.724 0 0 0 1.066 -2.573c-.94 -1.543 .826 -3.31 2.37 -2.37c1 .608 2.296 .07 2.572 -1.065", "M9 12a3 3 0 1 0 6 0a3 3 0 0 0 -6 0") }
    val Code by lazy { tabler("M7 8l-4 4l4 4", "M17 8l4 4l-4 4") }
    val Download by lazy { tabler("M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2 -2v-2", "M7 11l5 5l5 -5", "M12 4l0 12") }
    val Upload by lazy { tabler("M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2 -2v-2", "M7 9l5 -5l5 5", "M12 4l0 12") }
    val Home by lazy { tabler("M5 12l-2 0l9 -9l9 9l-2 0", "M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-7", "M9 21v-6a2 2 0 0 1 2 -2h2a2 2 0 0 1 2 2v6") }
    val MessageCircle by lazy { tabler("M3 20l1.3 -3.9c-2.324 -3.437 -1.426 -7.872 2.1 -10.374c3.526 -2.501 8.59 -2.296 11.845 .48c3.255 2.777 3.695 7.266 1.029 10.501c-2.666 3.235 -7.615 4.215 -11.574 2.293l-4.7 1") }
    val Folder by lazy { tabler("M5 4h4l3 3h7a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-11a2 2 0 0 1 2 -2") }
    val Cpu by lazy { tabler("M5 6a1 1 0 0 1 1 -1h12a1 1 0 0 1 1 1v12a1 1 0 0 1 -1 1h-12a1 1 0 0 1 -1 -1l0 -12", "M9 9h6v6h-6l0 -6", "M3 10h2", "M3 14h2", "M10 3v2", "M14 3v2", "M21 10h-2", "M21 14h-2", "M14 21v-2", "M10 21v-2") }
    val Database by lazy { tabler("M4 6a8 3 0 1 0 16 0a8 3 0 1 0 -16 0", "M4 6v6a8 3 0 0 0 16 0v-6", "M4 12v6a8 3 0 0 0 16 0v-6") }
    val Shield by lazy { tabler("M12 3a12 12 0 0 0 8.5 3a12 12 0 0 1 -8.5 15a12 12 0 0 1 -8.5 -15a12 12 0 0 0 8.5 -3") }
    val Books by lazy { tabler("M5 4m0 1a1 1 0 0 1 1 -1h2a1 1 0 0 1 1 1v14a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1z", "M9 4m0 1a1 1 0 0 1 1 -1h2a1 1 0 0 1 1 1v14a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1z", "M5 8h4", "M9 16h4", "M13.803 4.56l2.184 -.56c.55 -.14 1.11 .18 1.25 .71l3.54 13.77a1.01 1.01 0 0 1 -.73 1.23l-2.18 .56c-.55 .14 -1.11 -.18 -1.25 -.71l-3.54 -13.77a1.01 1.01 0 0 1 .73 -1.23z", "M14 9l4 -1", "M16 16l3.923 -1.006") }
    val World by lazy { tabler("M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0", "M3.6 9l16.8 0", "M3.6 15l16.8 0", "M11.5 3a17 17 0 0 0 0 18", "M12.5 3a17 17 0 0 1 0 18") }
    val Brain by lazy { tabler("M15.5 13a3.5 3.5 0 0 0 -3.5 3.5v1a3.5 3.5 0 0 0 7 0v-1.8", "M8.5 13a3.5 3.5 0 0 1 3.5 3.5v1a3.5 3.5 0 0 1 -7 0v-1.8", "M17.5 16a3.5 3.5 0 0 0 0 -7h-.5", "M19 9.3v-2.8a3.5 3.5 0 0 0 -7 0", "M6.5 16a3.5 3.5 0 0 1 0 -7h.5", "M5 9.3v-2.8a3.5 3.5 0 0 1 7 0v0", "M12 3v3") }
    val Link by lazy { tabler("M9 15l6 -6", "M11 6l.463 -.536a5 5 0 0 1 7.071 7.072l-.534 .464", "M13 18l-.397 .534a5.068 5.068 0 0 1 -7.127 0a4.972 4.972 0 0 1 0 -7.071l.524 -.463") }
    val ExternalLink by lazy { tabler("M12 6h-6a2 2 0 0 0 -2 2v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-6", "M11 13l9 -9", "M15 4h5v5") }
    val Send by lazy { tabler("M10 14l11 -11", "M21 3l-6.5 18a.55 .55 0 0 1 -1 0l-3.5 -7l-7 -3.5a.55 .55 0 0 1 0 -1l18 -6.5") }
    val Prompt by lazy { tabler("M8 9h8", "M8 13h6", "M9 18h-3a3 3 0 0 1 -3 -3v-8a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v8a3 3 0 0 1 -3 3h-3l-3 3l-3 -3") }
    val AlertTriangle by lazy { tabler("M12 9v4", "M10.363 3.591l-8.106 13.534a1.914 1.914 0 0 0 1.636 2.871h16.214a1.914 1.914 0 0 0 1.636 -2.87l-8.106 -13.536a1.914 1.914 0 0 0 -3.274 0", "M12 16h.01") }
}

private fun tabler(vararg paths: String): ImageVector {
    return ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        paths.forEach { svgPath ->
            addPath(
                pathData = PathParser().parsePathString(svgPath).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()
}
