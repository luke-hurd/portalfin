package org.jellyfin.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Shared portalfin header — the centered, tappable wordmark on a translucent
 * apron. Used by the home and the library/category screens so they look the
 * same. It's an OVERLAY: content scrolls behind it and dissolves under the
 * apron's tall fade (true backdrop-blur needs API 31; Portal is API 28, so a
 * gradient fade is the substitute). There is intentionally NO on-screen back
 * button or screen title — the Portal OSD back button handles navigation.
 *
 * The wordmark always returns to home via [onLogoClick] (a no-op on the home
 * screen itself). Reserve [HEADER_HEIGHT] of top padding in the scroll content
 * so the first row clears the logo.
 */
val HEADER_HEIGHT: Dp = 64.dp

// Simple square white logo (no wordmark). 43dp then -10% → ~39dp.
private val LOGO_SIZE = 39.dp

@Composable
fun PortalHeader(
    onLogoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Slightly darken the apron toward black so the white logo + Portal OSD
    // buttons stay legible over scrolled content. Lightened a touch — the white
    // logo no longer needs as heavy a backing.
    val tint = lerp(MaterialTheme.colorScheme.background, Color.Black, 0.35f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .background(
                Brush.verticalGradient(
                    0f to tint.copy(alpha = 0.85f),
                    0.5f to tint.copy(alpha = 0.7f),
                    0.8f to tint.copy(alpha = 0.4f),
                    1f to tint.copy(alpha = 0f),
                ),
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Small white-mark PNG, sized for the header slot (no big-image downscale).
        // NOTE: this sits in the Portal OSD band (top ~64px) which eats touches, so
        // it's branding only — taps don't register (the OSD overlay consumes them).
        // Settings/home navigation lives in tappable content below the band (the
        // home Settings card). onLogoClick kept for non-Portal devices.
        AsyncImage(
            model = "file:///android_asset/native/white-logo.png",
            contentDescription = "portalfin",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(top = 8.dp)
                .size(LOGO_SIZE)
                .clickable(onClick = onLogoClick),
        )
    }
}
