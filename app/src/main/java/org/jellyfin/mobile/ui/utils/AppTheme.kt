package org.jellyfin.mobile.ui.utils

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object PortalColors {
    val MetaBlue = Color(0xFF0866FF)
    val Background = Color(0xFF1A1A1A)
    val Surface = Color(0xFF2B2B2B)
    val SurfaceVariant = Color(0xFF202020)
    val OnBackground = Color(0xFFF0F0F0)
    val OnSurface = Color(0xFFDADADA)
    val Error = Color(0xFFCF6679)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colors = remember {
        darkColors(
            primary = PortalColors.MetaBlue,
            primaryVariant = PortalColors.SurfaceVariant,
            secondary = PortalColors.MetaBlue,
            background = PortalColors.Background,
            surface = PortalColors.Surface,
            error = PortalColors.Error,
            onPrimary = PortalColors.OnBackground,
            onSecondary = PortalColors.OnBackground,
            onBackground = PortalColors.OnBackground,
            onSurface = PortalColors.OnSurface,
            onError = PortalColors.OnBackground,
        )
    }
    MaterialTheme(
        colors = colors,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
