package com.santiya.localaihub.ui.screen.offlinecity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.santiya.localaihub.global.AppLanguageManager
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.global.localizedText
import com.santiya.localaihub.offlinecity.OfflineCityAnswer
import com.santiya.localaihub.offlinecity.OfflineCityBounds
import com.santiya.localaihub.offlinecity.OfflineCityMapMarker
import com.santiya.localaihub.offlinecity.OfflineCityMapPayload
import com.santiya.localaihub.offlinecity.OfflineCityMapPoint
import com.santiya.localaihub.ui.components.StandardCard
import com.santiya.localaihub.ui.icons.TnIcons
import kotlin.math.max

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OfflineCityAnswerCard(
    answer: OfflineCityAnswer,
    modifier: Modifier = Modifier,
    onSuggestionClick: (String) -> Unit,
) {
    val language = AppLanguageManager.readPersistedLanguage(LocalContext.current)
    StandardCard(
        modifier = modifier,
        title = answer.title,
        description = localizedText(language, "Офлайн-ответ по локальному городскому пакету", "Offline answer from the local city pack"),
        icon = TnIcons.World,
    ) {
        answer.mapPayload?.let { payload ->
            OfflineCityMapPanel(payload = payload)
        }
        Text(
            text = answer.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (answer.suggestions.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                answer.suggestions.forEach { suggestion ->
                    AssistChip(
                        onClick = { onSuggestionClick(suggestion) },
                        label = { Text(suggestion) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OfflineCityMapPanel(
    payload: OfflineCityMapPayload,
    modifier: Modifier = Modifier,
) {
    val language = AppLanguageManager.readPersistedLanguage(LocalContext.current)
    val bounds = payload.bounds ?: deriveBounds(payload.markers, payload.path, payload.focus) ?: return
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Standards.RadiusLg),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(PaddingValues(vertical = 10.dp)),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Standards.SpacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = localizedText(language, "Офлайн-карта", "Offline map"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurface,
                    modifier = Modifier.weight(1f)
                )
                payload.tileSource?.let { tileSource ->
                    Text(
                        text = tileSource.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = primary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.35f)
                    .padding(horizontal = Standards.SpacingSm)
                    .background(surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(Standards.RadiusLg))
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.35f)) {
                    val padding = 22f
                    val width = size.width - padding * 2
                    val height = size.height - padding * 2
                    val lonSpan = max(bounds.east - bounds.west, 0.0001)
                    val latSpan = max(bounds.north - bounds.south, 0.0001)

                    fun project(point: OfflineCityMapPoint): Offset {
                        val x = ((point.longitude - bounds.west) / lonSpan).toFloat()
                        val y = ((bounds.north - point.latitude) / latSpan).toFloat()
                        return Offset(
                            x = padding + x * width,
                            y = padding + y * height
                        )
                    }

                    drawRoundRect(
                        color = surfaceVariant.copy(alpha = 0.5f),
                        topLeft = Offset.Zero,
                        size = size,
                        cornerRadius = CornerRadius(24f, 24f)
                    )

                    val gridColor = outline.copy(alpha = 0.18f)
                    repeat(4) { index ->
                        val t = index / 3f
                        drawLine(gridColor, Offset(padding, padding + t * height), Offset(padding + width, padding + t * height), strokeWidth = 2f)
                        drawLine(gridColor, Offset(padding + t * width, padding), Offset(padding + t * width, padding + height), strokeWidth = 2f)
                    }

                    if (payload.path.size >= 2) {
                        val projected = payload.path.map(::project)
                        projected.zipWithNext().forEach { (from, to) ->
                            drawLine(primary, from, to, strokeWidth = 8f, cap = StrokeCap.Round)
                        }
                    }

                    payload.markers.forEach { marker ->
                        val offset = project(marker.point)
                        val markerColor = when (marker.kind) {
                            "user" -> primary
                            "stop", "destination_stop" -> secondary
                            "food" -> tertiary
                            else -> onSurface
                        }
                        drawCircle(color = markerColor, radius = if (marker.kind == "user") 16f else 12f, center = offset)
                        drawCircle(color = Color.White, radius = if (marker.kind == "user") 7f else 5f, center = offset)
                    }

                    payload.focus?.let { focus ->
                        drawCircle(
                            color = primary.copy(alpha = 0.18f),
                            radius = 26f,
                            center = project(focus),
                            style = Stroke(width = 6f)
                        )
                    }
                }
            }

            payload.markers.take(4).takeIf { it.isNotEmpty() }?.let { markers ->
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Standards.SpacingSm),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    markers.forEach { marker ->
                        Surface(
                            shape = RoundedCornerShape(Standards.RadiusFull),
                            color = surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = marker.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun deriveBounds(
    markers: List<OfflineCityMapMarker>,
    path: List<OfflineCityMapPoint>,
    focus: OfflineCityMapPoint?,
): OfflineCityBounds? {
    val points = buildList {
        addAll(markers.map { it.point })
        addAll(path)
        focus?.let(::add)
    }
    if (points.isEmpty()) return null
    return OfflineCityBounds(
        south = points.minOf { it.latitude },
        west = points.minOf { it.longitude },
        north = points.maxOf { it.latitude },
        east = points.maxOf { it.longitude },
    )
}
