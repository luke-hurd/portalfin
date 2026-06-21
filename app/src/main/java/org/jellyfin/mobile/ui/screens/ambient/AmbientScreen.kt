package org.jellyfin.mobile.ui.screens.ambient

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.delay
import org.jellyfin.mobile.ui.utils.PortalColors
import org.koin.androidx.compose.koinViewModel
import java.util.Calendar

private const val ROTATE_MS = 12_000L
private const val CROSSFADE_MS = 1_500
private const val CLOCK_TICK_MS = 10_000L

// Ken Burns: each slide slowly scales up and drifts. Direction alternates per
// slide so consecutive backdrops don't all pan the same way.
private const val KB_SCALE_FROM = 1.0f
private const val KB_SCALE_TO = 1.12f
private const val KB_DRIFT_PX = 40f

/**
 * Full-screen native ambient screensaver: a slow Ken-Burns slideshow of library
 * backdrops with a large clock, on the Portal design system. Shown by
 * [MainActivity] after an idle period; any touch dismisses it (handled by the
 * host). Returns nothing visible until at least one slide has loaded.
 */
@Composable
fun AmbientScreen(
    modifier: Modifier = Modifier,
    viewModel: AmbientViewModel = koinViewModel(),
) {
    val slides by viewModel.slides.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                // Bottom-up scrim so the clock + title stay legible over bright art.
                val brush = Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.55f to Color.Transparent,
                    1.0f to PortalColors.Background.copy(alpha = 0.85f),
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(brush)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (slides.isNotEmpty()) {
            var index by remember { mutableIntStateOf(0) }

            // Advance the slide on a fixed cadence.
            LaunchedEffect(slides) {
                index = 0
                while (true) {
                    delay(ROTATE_MS)
                    index = (index + 1) % slides.size
                }
            }

            // Crossfade between slides; each incoming slide runs its own Ken Burns.
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    fadeIn(tween(CROSSFADE_MS)) togetherWith fadeOut(tween(CROSSFADE_MS))
                },
                label = "ambient-slide",
            ) { i ->
                KenBurnsBackdrop(slide = slides[i], slideIndex = i)
            }
        }

        AmbientClock(modifier = Modifier.align(Alignment.BottomStart).padding(48.dp))
    }
}

/** One backdrop with a continuous slow zoom + drift over its time on screen. */
@Composable
private fun KenBurnsBackdrop(slide: AmbientSlide, slideIndex: Int) {
    // Drive progress 0f -> 1f once, on mount. (An Animatable in a LaunchedEffect
    // actually animates; updateTransition(targetState = true) does NOT — its
    // state never changes, so it snaps straight to the end and nothing moves.)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slide.backdropUrl) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = ROTATE_MS.toInt() + CROSSFADE_MS, easing = LinearEasing),
        )
    }

    // Alternate drift direction per slide for variety.
    val driftSign = if (slideIndex % 2 == 0) 1f else -1f
    val scale = KB_SCALE_FROM + (KB_SCALE_TO - KB_SCALE_FROM) * progress.value
    val translateX = driftSign * KB_DRIFT_PX * (progress.value - 0.5f) * 2f

    val context = LocalContext.current
    val request = remember(slide.backdropUrl) {
        ImageRequest.Builder(context)
            .data(slide.backdropUrl)
            .crossfade(true)
            .build()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = request,
            contentDescription = slide.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = translateX
                },
        )
        AmbientTitle(
            slide = slide,
            modifier = Modifier.align(Alignment.BottomEnd).padding(48.dp),
        )
    }
}

/** The title-art logo (preferred) or text title + year, bottom-right. */
@Composable
private fun AmbientTitle(slide: AmbientSlide, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (slide.logoUrl != null) {
        val request = remember(slide.logoUrl) {
            ImageRequest.Builder(context).data(slide.logoUrl).crossfade(true).build()
        }
        AsyncImage(
            model = request,
            contentDescription = slide.title,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(width = 280.dp, height = 90.dp),
        )
    } else {
        Text(
            text = slide.title + (slide.year?.let { "  ·  $it" } ?: ""),
            color = PortalColors.OnBackground,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp),
            textAlign = TextAlign.End,
            modifier = modifier,
        )
    }
}

/** Big time + date, refreshed every 10s (we only show minute precision). */
@Composable
private fun AmbientClock(modifier: Modifier = Modifier) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(CLOCK_TICK_MS)
        }
    }
    val cal = remember(nowMs) { Calendar.getInstance().apply { timeInMillis = nowMs } }

    val hour24 = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val hour12 = ((hour24 + 11) % 12) + 1
    val ampm = if (hour24 >= 12) "pm" else "am"
    val timeText = "$hour12:${minute.toString().padStart(2, '0')} $ampm"

    val days = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val dayName = days[cal.get(Calendar.DAY_OF_WEEK) - 1]
    val dateText = "$dayName, ${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.DAY_OF_MONTH)}"

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Base on the theme's Inter styles and override only size — a bare Text
        // would fall back to the system font, not Inter.
        Text(
            text = timeText,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 88.sp, lineHeight = 92.sp),
        )
        Text(
            text = dateText,
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 24.sp, lineHeight = 28.sp),
        )
    }
}
