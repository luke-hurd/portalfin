package org.jellyfin.mobile.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

private const val PRESS_SCALE = 0.94f
private const val PRESS_DOWN_MS = 90
private const val PRESS_UP_MS = 120

/**
 * Shared card tap feedback. On a tap (not long-press), play a quick squish
 * (scale down then back) and only fire [onClick] AFTER the animation finishes —
 * so a screen transition doesn't cut the animation off. The card also tracks the
 * finger while held. Used by both the home cards and the library poster grid so
 * the interaction is identical everywhere.
 */
fun Modifier.pressable(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // Scale is driven entirely by the press state: down while held, back to 1f on
    // release OR cancel (e.g. when the finger moves into a scroll). This can't get
    // stuck shrunk — there's no separate click-time animation to leave it down.
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(if (pressed) PRESS_DOWN_MS else PRESS_UP_MS),
        label = "pressScale",
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
}
