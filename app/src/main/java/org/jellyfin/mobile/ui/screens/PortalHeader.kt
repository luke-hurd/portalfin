package org.jellyfin.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
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
 * gradient fade is the substitute).
 *
 * On the Portal OEM OS, the back/home OSD pills are drawn by the system in the
 * top-left band, so there's NO on-screen back button — the centered wordmark is
 * branding only ([onLogoClick] kept for non-Portal devices). Under the Immortal
 * launcher there is no OEM OSD, so when [showImmortalNav] is true we draw our own
 * back + home pills (styled to match the OEM ones) on the left.
 *
 * Reserve [HEADER_HEIGHT] of top padding in the scroll content so the first row
 * clears the logo.
 */
val HEADER_HEIGHT: Dp = 64.dp

// Simple square white logo (no wordmark). 43dp then -10% → ~39dp.
private val LOGO_SIZE = 39.dp

// OEM Portal OSD pill geometry, measured from device screenshots (1280x800,
// density 1.0 so px≈dp): a capsule (fully-rounded), ~86x43, translucent WHITE
// fill (you can see the wallpaper through it as content scrolls), centered white
// icon. We mirror size/shape/color/iconography as closely as we can.
private val PILL_HEIGHT = 44.dp
private val PILL_WIDTH = 86.dp
private val PILL_CORNER = 22.dp // = height/2 → full capsule
private val PILL_ICON = 22.dp
private val PILL_GAP = 17.dp
private val NAV_EDGE_PADDING = 21.dp
// Frosted translucent white — reads as the OEM's ~rgb(31,32,34)-over-black pill
// while staying see-through over bright content.
private val PILL_FILL = Color(0x33FFFFFF) // ~20% white

@Composable
fun PortalHeader(
    onLogoClick: () -> Unit,
    modifier: Modifier = Modifier,
    showImmortalNav: Boolean = false,
    onBack: () -> Unit = {},
    onHome: () -> Unit = {},
) {
    // Slightly darken the apron toward black so the white logo + nav pills stay
    // legible over scrolled content.
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
        // Centered white-mark PNG. On OEM Portal it's branding-only (the OSD band
        // eats touches); under Immortal nav it still returns to home on tap.
        AsyncImage(
            model = "file:///android_asset/native/white-logo.png",
            contentDescription = "portalfin",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(top = 8.dp)
                .size(LOGO_SIZE)
                .clickable(onClick = onLogoClick),
        )

        // Our own back/home pills, only under Immortal (no OEM OSD to clear).
        if (showImmortalNav) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = NAV_EDGE_PADDING, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(PILL_GAP),
            ) {
                NavPill(
                    icon = Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = "Back",
                    onClick = onBack,
                )
                NavPill(
                    icon = Icons.Filled.Home,
                    contentDescription = "Home",
                    onClick = onHome,
                )
            }
        }
    }
}

/** A single OEM-style OSD pill: stadium shape, translucent dark fill, white icon. */
@Composable
private fun NavPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = PILL_WIDTH, height = PILL_HEIGHT)
            .background(PILL_FILL, RoundedCornerShape(PILL_CORNER))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(PILL_ICON),
        )
    }
}
