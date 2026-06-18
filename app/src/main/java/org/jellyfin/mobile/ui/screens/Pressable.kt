package org.jellyfin.mobile.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // While the finger is down, hold the scaled-down state for live feedback.
    LaunchedEffect(pressed) {
        if (pressed) scale.animateTo(PRESS_SCALE, tween(PRESS_DOWN_MS))
    }

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
        ) {
            scope.launch {
                scale.animateTo(PRESS_SCALE, tween(PRESS_DOWN_MS))
                scale.animateTo(1f, tween(PRESS_UP_MS))
                onClick()
            }
        }
}
