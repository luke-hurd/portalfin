package org.jellyfin.mobile.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
private val BACKDROP_HEIGHT = 420.dp
private val CONTENT_TOP_OFFSET = 260.dp
private val SECTION_SPACING = 16.dp
private const val BACKDROP_WIDTH_PX = 1280
private const val SCRIM_START = 0.4f
private const val TICKS_PER_MINUTE = 600_000_000L

@Composable
fun DetailScreen(
    onPlay: (BaseItemDto) -> Unit,
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
    onPlay: (BaseItemDto) -> Unit,
    topContentPadding: androidx.compose.ui.unit.Dp,
) {
    val apiClient: ApiClient = koinInject()
    val context = LocalContext.current

    val backdrop = remember(apiClient, item.id, context) {
        val type = if (!item.backdropImageTags.isNullOrEmpty()) ImageType.BACKDROP else ImageType.PRIMARY
        val url = apiClient.imageApi.getItemImageUrl(
            itemId = item.id,
            imageType = type,
            maxWidth = BACKDROP_WIDTH_PX,
        )
        ImageRequest.Builder(context).data(url).crossfade(true).build()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Hero backdrop, top-aligned, with a scrim fading into the page so text
        // below it stays legible.
        AsyncImage(
            model = backdrop,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(BACKDROP_HEIGHT)
                .align(Alignment.TopCenter),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BACKDROP_HEIGHT)
                .align(Alignment.TopCenter)
                .drawWithCache {
                    val brush = Brush.verticalGradient(
                        SCRIM_START to Color.Transparent,
                        1f to PortalColors.Background,
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush)
                    }
                },
        )

        DetailInfo(item, onPlay, topContentPadding)
    }
}

@Composable
private fun DetailInfo(
    item: BaseItemDto,
    onPlay: (BaseItemDto) -> Unit,
    topContentPadding: androidx.compose.ui.unit.Dp,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = topContentPadding + CONTENT_TOP_OFFSET, bottom = EDGE_PADDING)
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

        PlayButton(onClick = { onPlay(item) })

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

@Composable
private fun PlayButton(onClick: () -> Unit) {
    // M3 Button (pill), Meta blue. heightIn(min=52dp) per the Portal style guide.
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = PortalColors.MetaBlue),
        modifier = Modifier.heightIn(min = 52.dp).widthIn(min = 160.dp),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null)
        Text(text = "  Play", style = MaterialTheme.typography.labelLarge)
    }
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
