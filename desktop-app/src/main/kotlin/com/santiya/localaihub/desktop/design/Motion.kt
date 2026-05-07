package com.santiya.localaihub.desktop.design

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.Alignment

object Motion {
    private val iosEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    fun <T> interactive(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.7f,
        stiffness = 500f
    )

    fun <T> content(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMedium
    )

    fun <T> state(): FiniteAnimationSpec<T> = tween(
        durationMillis = 200,
        easing = iosEasing
    )

    fun <T> entrance(): FiniteAnimationSpec<T> = tween(
        durationMillis = 350,
        easing = iosEasing
    )

    fun <T> exit(): FiniteAnimationSpec<T> = tween(
        durationMillis = 200,
        easing = iosEasing
    )

    val Enter: EnterTransition = fadeIn(tween(300)) +
        expandVertically(tween(300), expandFrom = Alignment.Top)

    val Exit: ExitTransition = fadeOut(tween(200)) +
        shrinkVertically(tween(200), shrinkTowards = Alignment.Top)
}
