package org.jellyfin.mobile.ui.utils

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jellyfin.mobile.R

/**
 * Portal-native theme. Follows Meta's portal-samples design guide exactly
 * (Material 3 + Inter + Meta blue, dark always, no dynamic color). See memory:
 * portal-native-input-rule. PortalColors is kept for screens that reference exact
 * shades directly (card backgrounds, scrims); new code should prefer
 * MaterialTheme.colorScheme where possible.
 */
object PortalColors {
    val MetaBlue = Color(0xFF0866FF)
    val Background = Color(0xFF1A1A1A)
    val Surface = Color(0xFF2B2B2B)
    val SurfaceVariant = Color(0xFF202020)
    val OnBackground = Color(0xFFF0F0F0)
    val OnSurface = Color(0xFFDADADA)
    val Error = Color(0xFFCF6679)
    val Warning = Color(0xFFE3B341)
}

// Meta blue palette + dark surfaces, matching portal-samples Color.kt.
private val OnMetaBlue = Color(0xFFF0F0F0)
private val MetaBlueDarkCont = Color(0xFF004CB0)
private val OnMetaBlueDarkC = Color(0xFFD4E3FF)
private val NeutralGreyDark = Color(0xFFBEC6DC)

private val PortalDarkColorScheme = darkColorScheme(
    primary = PortalColors.MetaBlue,
    onPrimary = OnMetaBlue,
    primaryContainer = MetaBlueDarkCont,
    onPrimaryContainer = OnMetaBlueDarkC,
    secondary = NeutralGreyDark,
    onSecondary = OnMetaBlue,
    background = PortalColors.Background,
    surface = PortalColors.Surface,
    onBackground = PortalColors.OnBackground,
    onSurface = PortalColors.OnSurface,
    error = PortalColors.Error,
    onError = OnMetaBlue,
)

// Inter via XML downloadable fonts (res/font/inter*.xml). Min 14sp, body 18sp,
// only Normal/Medium/Bold — per the style guide.
private val InterFontFamily = FontFamily(
    Font(R.font.inter, weight = FontWeight.Normal),
    Font(R.font.inter_medium, weight = FontWeight.Medium),
    Font(R.font.inter_bold, weight = FontWeight.Bold),
)

private val PortalTypography = Typography(
    headlineSmall = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleMedium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    labelMedium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    // Portal is always dark; dynamic color is never used (it would override the
    // Meta palette on API 31+).
    MaterialTheme(
        colorScheme = PortalDarkColorScheme,
        typography = PortalTypography,
        content = content,
    )
}
