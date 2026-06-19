package org.jellyfin.mobile.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SHIMMER_BAND = 220.dp
private const val SHIMMER_TRAVEL_BANDS = 3

/**
 * Loading-skeleton shimmer: a base fill with a bright highlight band that sweeps
 * diagonally across, so a placeholder reads as actively loading (not just a dim
 * static square). Use as the background of a clipped Box.
 *
 *   Box(Modifier.clip(shape).shimmer())
 *
 * Self-contained: it animates on its own via an infinite transition.
 */
fun Modifier.shimmer(): Modifier = composed {
    val base = Color.White.copy(alpha = 0.07f)
    val highlight = Color.White.copy(alpha = 0.22f)

    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )

    // Sweep a diagonal band from off-screen top-left to off-screen bottom-right.
    // Travel = 3 band-widths so it fully clears even wide placeholders.
    val bandPx = with(androidx.compose.ui.platform.LocalDensity.current) { SHIMMER_BAND.toPx() }
    val start = -bandPx + progress * (bandPx * SHIMMER_TRAVEL_BANDS)
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(start, start),
        end = Offset(start + bandPx, start + bandPx),
    )
    this.background(base).background(brush)
}
