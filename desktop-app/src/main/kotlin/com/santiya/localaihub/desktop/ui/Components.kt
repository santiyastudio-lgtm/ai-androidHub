package com.santiya.localaihub.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.santiya.localaihub.desktop.design.HubIcons
import com.santiya.localaihub.desktop.design.Motion
import com.santiya.localaihub.desktop.design.Standards
import com.santiya.localaihub.desktop.design.canvasBorder
import com.santiya.localaihub.desktop.design.glassSurface
import com.santiya.localaihub.desktop.design.heroGradientEnd
import com.santiya.localaihub.desktop.design.heroGradientStart

@Composable
fun AppBackdrop(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        scheme.background,
                        scheme.primaryContainer.copy(alpha = 0.22f),
                        scheme.surface.copy(alpha = 0.96f),
                        scheme.background
                    )
                )
            )
    ) {
        val glowTransition = rememberInfiniteTransition(label = "glow")
        val glowShift by glowTransition.animateFloat(
            initialValue = 0.12f,
            targetValue = 0.38f,
            animationSpec = infiniteRepeatable(tween(4200)),
            label = "glow_shift"
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(scheme.primary.copy(alpha = glowShift), Color.Transparent)
                ),
                radius = size.minDimension * 0.35f,
                center = center.copy(x = size.width * 0.2f, y = size.height * 0.18f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(scheme.tertiary.copy(alpha = glowShift * 0.9f), Color.Transparent)
                ),
                radius = size.minDimension * 0.30f,
                center = center.copy(x = size.width * 0.85f, y = size.height * 0.22f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(scheme.secondary.copy(alpha = glowShift * 0.75f), Color.Transparent)
                ),
                radius = size.minDimension * 0.42f,
                center = center.copy(x = size.width * 0.55f, y = size.height * 0.92f)
            )
        }
        content()
    }
}

@Composable
fun ProductCanvas(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .border(1.dp, MaterialTheme.colorScheme.canvasBorder, RoundedCornerShape(44.dp)),
        shape = RoundedCornerShape(44.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 8.dp,
        shadowElevation = 24.dp
    ) {
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .border(1.dp, accent.copy(alpha = 0.14f), RoundedCornerShape(Standards.RadiusXxl)),
        shape = RoundedCornerShape(Standards.RadiusXxl),
        color = MaterialTheme.colorScheme.glassSurface,
        tonalElevation = 2.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.03f),
                            Color.Transparent
                        )
                    )
                )
                .padding(Standards.SpacingLg)
        ) {
            content()
        }
    }
}

@Composable
fun HeroCard(
    title: String,
    body: String,
    badge: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(34.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.heroGradientStart,
                            MaterialTheme.colorScheme.heroGradientEnd
                        )
                    )
                )
                .padding(Standards.SpacingXl)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)) {
                StatusChip(
                    label = badge,
                    icon = HubIcons.Sparkles,
                    container = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                    content = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
        },
        animationSpec = Motion.state(),
        label = "action_bg"
    )
    Surface(
        modifier = modifier
            .size(Standards.ActionIconSize)
            .clip(CircleShape)
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                },
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        color = background,
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun PrimaryCtaButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatusChip(
    label: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    content: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = modifier,
        color = container,
        shape = RoundedCornerShape(Standards.RadiusFull)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            }
            Text(label, color = content, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun NavigationPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Standards.RadiusFull),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.86f)
        }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun DynamicIslandPill(
    currentModelName: String?,
    isGenerating: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val errorColor = MaterialTheme.colorScheme.error
    val errorContainer = MaterialTheme.colorScheme.errorContainer
    val title = when {
        isExpanded -> "Панель AI"
        isGenerating -> "Генерация ответа"
        currentModelName.isNullOrBlank() -> "Модель не выбрана"
        else -> currentModelName
    }
    val subtitle = when {
        isExpanded -> "Нажмите, чтобы свернуть"
        isGenerating -> "Модель обрабатывает запрос"
        currentModelName.isNullOrBlank() -> "Откройте Setup или Store"
        else -> "Модель активна"
    }
    val accent = when {
        isGenerating -> Color(0xFFA855F7)
        currentModelName.isNullOrBlank() -> errorColor
        else -> MaterialTheme.colorScheme.primary
    }
    val container = when {
        isExpanded -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f)
        isGenerating -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.96f)
        currentModelName.isNullOrBlank() -> errorContainer.copy(alpha = 0.82f)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f)
    }
    val contentColor = when {
        currentModelName.isNullOrBlank() -> errorColor
        isGenerating -> Color(0xFF644698)
        else -> MaterialTheme.colorScheme.primary
    }
    val targetWidth = when {
        isExpanded -> 252.dp
        isGenerating -> 212.dp
        currentModelName.isNullOrBlank() -> 220.dp
        else -> 192.dp
    }
    val width by animateDpAsState(targetValue = targetWidth, animationSpec = Motion.content(), label = "pill_width")
    val height by animateDpAsState(targetValue = if (isExpanded) 54.dp else 46.dp, animationSpec = Motion.content(), label = "pill_height")
    val chevronRotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, animationSpec = Motion.state(), label = "pill_rotation")
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(0.82f, 1.18f, infiniteRepeatable(tween(900)), label = "pill_pulse_scale")
    val pulseAlpha by pulse.animateFloat(0.34f, 0.88f, infiniteRepeatable(tween(900)), label = "pill_pulse_alpha")

    Surface(
        onClick = onClick,
        color = container,
        shadowElevation = if (isExpanded) 10.dp else 6.dp,
        shape = RoundedCornerShape(Standards.RadiusFull),
        modifier = modifier
            .width(width)
            .height(height)
            .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(Standards.RadiusFull))
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = Standards.SpacingMd, vertical = Standards.SpacingXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                if (isGenerating) {
                    Box(
                        modifier = Modifier
                            .size((12f * pulseScale).dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = pulseAlpha))
                    )
                }
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            }
            AnimatedContent(
                targetState = title to subtitle,
                transitionSpec = { fadeIn(Motion.state()) togetherWith fadeOut(Motion.exit()) },
                label = "pill_content"
            ) { content ->
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = content.first,
                        color = contentColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = content.second,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Standards.SpacingXs), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(
                    label = "AI",
                    container = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                    content = contentColor
                )
                Icon(
                    HubIcons.ChevronDown,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.78f),
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = chevronRotation }
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrimaryCtaButton(label = actionLabel, onClick = onAction, modifier = Modifier.fillMaxWidth(0.46f))
        }
    }
}

@Composable
fun SectionTitle(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
