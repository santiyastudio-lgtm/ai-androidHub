package com.santiya.localaihub.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.ui.icons.TnIcons
import com.santiya.localaihub.ui.theme.Motion

private data class DynamicIslandVisualState(
    val title: String,
    val subtitle: String,
    val leadingTint: Color,
    val containerColor: Color,
    val borderColor: Color,
    val showPulse: Boolean
)

@Composable
internal fun DynamicIslandPill(
    currentModelName: String?,
    isGenerating: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val errorColor = MaterialTheme.colorScheme.error
    val errorContainer = MaterialTheme.colorScheme.errorContainer
    val visualState = remember(currentModelName, isGenerating, isExpanded) {
        when {
            isExpanded -> DynamicIslandVisualState(
                title = "Панель AI",
                subtitle = "Нажмите, чтобы свернуть",
                leadingTint = Color(0xFFB794F6),
                containerColor = Color(0xFF0A0A0F),
                borderColor = Color.White.copy(alpha = 0.08f),
                showPulse = false
            )

            isGenerating -> DynamicIslandVisualState(
                title = "Генерация ответа",
                subtitle = "Модель обрабатывает запрос",
                leadingTint = Color(0xFFA855F7),
                containerColor = Color(0xFF140C1D),
                borderColor = Color(0x66A855F7),
                showPulse = true
            )

            currentModelName.isNullOrBlank() -> DynamicIslandVisualState(
                title = "Модель не выбрана",
                subtitle = "Откройте панель и выберите модель",
                leadingTint = errorColor,
                containerColor = errorContainer
                    .copy(alpha = 0.82f)
                    .compositeOver(Color.Black),
                borderColor = errorColor.copy(alpha = 0.24f),
                showPulse = false
            )

            else -> DynamicIslandVisualState(
                title = currentModelName,
                subtitle = "Модель активна",
                leadingTint = Color(0xFF6EE7B7),
                containerColor = Color(0xFF0C1113),
                borderColor = Color.White.copy(alpha = 0.07f),
                showPulse = false
            )
        }
    }

    val targetWidth = when {
        isExpanded -> 250.dp
        isGenerating -> 212.dp
        currentModelName.isNullOrBlank() -> 214.dp
        else -> 188.dp
    }
    val width by androidx.compose.animation.core.animateDpAsState(
        targetValue = targetWidth,
        animationSpec = Motion.content(),
        label = "island_width"
    )
    val height by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isExpanded) 54.dp else 46.dp,
        animationSpec = Motion.content(),
        label = "island_height"
    )
    val containerColor by animateColorAsState(
        targetValue = visualState.containerColor,
        animationSpec = Motion.state(),
        label = "island_container"
    )
    val borderColor by animateColorAsState(
        targetValue = visualState.borderColor,
        animationSpec = Motion.state(),
        label = "island_border"
    )
    val pulseTransition = rememberInfiniteTransition(label = "island_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 900)),
        label = "island_pulse_scale"
    )
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.34f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 900)),
        label = "island_pulse_alpha"
    )
    val trailingRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = Motion.state(),
        label = "island_chevron_rotation"
    )

    Surface(
        onClick = onClick,
        color = containerColor,
        shadowElevation = if (isExpanded) 12.dp else 8.dp,
        shape = RoundedCornerShape(Standards.RadiusFull),
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(Standards.RadiusFull))
            .border(1.dp, borderColor, RoundedCornerShape(Standards.RadiusFull))
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = Standards.SpacingMd, vertical = Standards.SpacingXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            Box(
                modifier = Modifier.size(18.dp),
                contentAlignment = Alignment.Center
            ) {
                if (visualState.showPulse) {
                    Box(
                        modifier = Modifier
                            .size((12f * pulseScale).dp)
                            .clip(RoundedCornerShape(Standards.RadiusFull))
                            .background(visualState.leadingTint.copy(alpha = pulseAlpha))
                    )
                }
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(Standards.RadiusFull))
                        .background(visualState.leadingTint)
                )
            }

            AnimatedContent(
                targetState = visualState,
                transitionSpec = {
                    androidx.compose.animation.fadeIn(Motion.state()) togetherWith
                        androidx.compose.animation.fadeOut(Motion.exit())
                },
                label = "island_content"
            ) { state ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = state.title,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.subtitle,
                        color = Color.White.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingXs)
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(Standards.RadiusFull)
                ) {
                    Text(
                        text = "AI",
                        color = Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = TnIcons.ChevronDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.78f),
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = trailingRotation }
                )
            }
        }
    }
}
