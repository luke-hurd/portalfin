package org.jellyfin.mobile.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jellyfin.mobile.ui.utils.PortalColors
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.koin.compose.koinInject

private val EDGE_PADDING = 32.dp
private val SECTION_SPACING = 16.dp
private const val BACKDROP_WIDTH_PX = 1280
private const val HERO_ASPECT = 16f / 9f
private const val TICKS_PER_MINUTE = 600_000_000L

@Composable
fun DetailScreen(
    onPlay: (BaseItemDto, startTicks: Long) -> Unit,
    viewModel: DetailViewModel,
    topContentPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            is DetailState.Loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
            )
            is DetailState.Error -> Text(
                text = current.message,
                color = PortalColors.Error,
                modifier = Modifier.align(Alignment.Center).padding(EDGE_PADDING),
            )
            is DetailState.Content -> DetailContent(current.item, onPlay, topContentPadding)
        }
    }
}

@Composable
private fun DetailContent(
    item: BaseItemDto,
    onPlay: (BaseItemDto, Long) -> Unit,
    topContentPadding: androidx.compose.ui.unit.Dp,
) {
    val apiClient: ApiClient = koinInject()
    val context = LocalContext.current

    // Prefer the wide BACKDROP (cinematic, what jellyfin-web shows up top); fall
    // back to PRIMARY only when there's no backdrop.
    val hero = remember(apiClient, item.id, context) {
        val type = if (!item.backdropImageTags.isNullOrEmpty()) ImageType.BACKDROP else ImageType.PRIMARY
        val url = apiClient.imageApi.getItemImageUrl(
            itemId = item.id,
            imageType = type,
            maxWidth = BACKDROP_WIDTH_PX,
        )
        ImageRequest.Builder(context).data(url).crossfade(true).build()
    }

    // The hero scrolls WITH the content (it's the first item in the scroll
    // column), sits behind nothing, shows the WHOLE image (Fit, full 16:9), and
    // fades into the page background at its lower edge.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(HERO_ASPECT)) {
            AsyncImage(
                model = hero,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            // Fade only the bottom strip into the background so the content below
            // blends in — keep most of the image clear.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val brush = Brush.verticalGradient(
                            0.7f to Color.Transparent,
                            1f to PortalColors.Background,
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(brush)
                        }
                    },
            )
        }

        DetailInfo(item, onPlay)
    }
}

@Composable
private fun DetailInfo(
    item: BaseItemDto,
    onPlay: (BaseItemDto, Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Pull up so the content overlaps the hero's faded bottom edge.
            .padding(top = 0.dp, bottom = EDGE_PADDING)
            .padding(horizontal = EDGE_PADDING),
        verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
    ) {
        Text(
            text = item.name.orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
        )

        val meta = metaLine(item)
        if (meta.isNotEmpty()) {
            Text(text = meta, style = MaterialTheme.typography.bodyMedium, color = PortalColors.OnSurface)
        }

        PlayActions(item, onPlay)

        item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyLarge,
                color = PortalColors.OnBackground,
            )
        }

        item.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
            Text(
                text = genres.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = PortalColors.OnSurface,
            )
        }
    }
}

/**
 * Play actions. With saved progress: "Resume HH:MM" (primary) + "Start Over"
 * (outlined). Otherwise a single "Play". All M3, heightIn(min=52dp) per guide.
 */
@Composable
private fun PlayActions(
    item: BaseItemDto,
    onPlay: (BaseItemDto, Long) -> Unit,
) {
    val resumeTicks = item.userData?.playbackPositionTicks ?: 0L
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (resumeTicks > 0) {
            Button(
                onClick = { onPlay(item, resumeTicks) },
                colors = ButtonDefaults.buttonColors(containerColor = PortalColors.MetaBlue),
                modifier = Modifier.heightIn(min = 52.dp).widthIn(min = 160.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(text = "  Resume ${formatTicks(resumeTicks)}", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = { onPlay(item, 0L) },
                modifier = Modifier.heightIn(min = 52.dp),
            ) {
                Text(text = "Start Over", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Button(
                onClick = { onPlay(item, 0L) },
                colors = ButtonDefaults.buttonColors(containerColor = PortalColors.MetaBlue),
                modifier = Modifier.heightIn(min = 52.dp).widthIn(min = 160.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(text = "  Play", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Ticks → "1:23:45" or "4:05". */
private fun formatTicks(ticks: Long): String {
    val totalSeconds = (ticks / 10_000_000L).toInt()
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "2024 · 2h 14m · PG-13 · ★ 8.1" — only the parts the item actually has. */
private fun metaLine(item: BaseItemDto): String {
    val parts = mutableListOf<String>()
    item.productionYear?.let { parts.add(it.toString()) }
    item.runTimeTicks?.let { ticks ->
        val totalMinutes = (ticks / TICKS_PER_MINUTE).toInt()
        if (totalMinutes > 0) {
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            parts.add(if (h > 0) "${h}h ${m}m" else "${m}m")
        }
    }
    item.officialRating?.let { parts.add(it) }
    item.communityRating?.let { parts.add("★ %.1f".format(it)) }
    return parts.joinToString("  ·  ")
}
