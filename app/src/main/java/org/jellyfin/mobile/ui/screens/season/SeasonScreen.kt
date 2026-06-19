package org.jellyfin.mobile.ui.screens.season

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jellyfin.mobile.ui.screens.pressable
import org.jellyfin.mobile.ui.utils.PortalColors
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.koin.compose.koinInject

private val EDGE_PADDING = 32.dp
private val SECTION_SPACING = 24.dp
private const val BACKDROP_WIDTH_PX = 1280
private const val EPISODE_IMG_PX = 400
private const val EPISODE_COLUMNS = 3
private val SEASON_CARD_WIDTH = 130.dp
private const val SEASON_IMG_PX = 300
private const val CONTENT_TOP_FRACTION = 0.42f

/**
 * Season detail — a full detail page (like the movie/series ones): the season
 * image fills the background and fades out; content sits on top. Episodes are a
 * GRID (thumbnail + name + short description); a season selector follows.
 */
@Composable
fun SeasonScreen(
    onEpisodeClick: (BaseItemDto) -> Unit,
    onSeasonClick: (BaseItemDto) -> Unit,
    viewModel: SeasonViewModel,
    topContentPadding: Dp = 0.dp,
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            is SeasonState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            is SeasonState.Error -> Text(
                text = current.message,
                color = PortalColors.Error,
                modifier = Modifier.align(Alignment.Center).padding(EDGE_PADDING),
            )
            is SeasonState.Content -> SeasonContent(current, onEpisodeClick, onSeasonClick, viewModel, topContentPadding)
        }
    }
}

@Composable
private fun SeasonContent(
    content: SeasonState.Content,
    onEpisodeClick: (BaseItemDto) -> Unit,
    onSeasonClick: (BaseItemDto) -> Unit,
    viewModel: SeasonViewModel,
    topContentPadding: Dp,
) {
    val apiClient: ApiClient = koinInject()
    val context = LocalContext.current
    val season = content.season
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Season-download confirm dialog: null = closed; otherwise the sized plan.
    var downloadPlan by remember { mutableStateOf<SeasonDownloadPlan?>(null) }
    if (downloadPlan != null) {
        SeasonDownloadDialog(
            plan = downloadPlan!!,
            onConfirm = { quality ->
                viewModel.downloadSeason(quality)
                downloadPlan = null
            },
            onDismiss = { downloadPlan = null },
        )
    }

    val hero = remember(apiClient, season.id) {
        // Prefer the season's own backdrop, else the series', else the poster.
        val (id, type) = when {
            !season.backdropImageTags.isNullOrEmpty() -> season.id to ImageType.BACKDROP
            season.parentBackdropItemId != null -> season.parentBackdropItemId!! to ImageType.BACKDROP
            else -> season.id to ImageType.PRIMARY
        }
        val url = apiClient.imageApi.getItemImageUrl(itemId = id, imageType = type, maxWidth = BACKDROP_WIDTH_PX)
        ImageRequest.Builder(context).data(url).crossfade(true).build()
    }

    // The LazyVerticalGrid is the single scroll container. The background image is
    // a pinned layer BEHIND it (drawn in the Box), and the grid's top content
    // padding leaves the upper part of that image visible; as you scroll, the
    // content rises over the faded image. (See memory: detail-hero-spec.)
    Box(modifier = Modifier.fillMaxSize()) {
        // Background image layer — fullscreen, top-aligned, fades out at the bottom.
        Box(modifier = Modifier.fillMaxWidth().height(screenHeight)) {
            AsyncImage(
                model = hero,
                contentDescription = season.name,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier.fillMaxSize().drawWithCache {
                    val brush = Brush.verticalGradient(
                        0.25f to Color.Transparent,
                        0.6f to PortalColors.Background.copy(alpha = 0.75f),
                        0.8f to PortalColors.Background,
                    )
                    onDrawWithContent { drawContent(); drawRect(brush) }
                },
            )
        }

        // Content on top — episode grid + season selector.
        LazyVerticalGrid(
            columns = GridCells.Fixed(EPISODE_COLUMNS),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = EDGE_PADDING,
                end = EDGE_PADDING,
                top = topContentPadding + screenHeight * CONTENT_TOP_FRACTION,
                bottom = EDGE_PADDING,
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    season.seriesName?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = PortalColors.OnSurface)
                    }
                    Text(
                        text = season.name.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    season.overview?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = PortalColors.OnBackground)
                    }
                    // Download-whole-season — sizes against free space, then confirms.
                    Button(
                        onClick = { downloadPlan = viewModel.planSeasonDownload() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PortalColors.Surface,
                            contentColor = PortalColors.OnBackground,
                        ),
                        modifier = Modifier.heightIn(min = 52.dp).padding(top = 12.dp),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Text(
                            text = "  Download Season",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Text(
                        text = "Episodes",
                        style = MaterialTheme.typography.titleMedium,
                        color = PortalColors.OnBackground,
                        modifier = Modifier.padding(top = SECTION_SPACING, bottom = 4.dp),
                    )
                }
            }

            items(content.episodes.distinctBy { it.id }, key = { it.id.toString() }, contentType = { "episode" }) { ep ->
                EpisodeCard(ep, onClick = { onEpisodeClick(ep) })
            }

            // Other seasons selector.
            val otherSeasons = content.seasons.filter { it.id != season.id }
            if (otherSeasons.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Other Seasons",
                        style = MaterialTheme.typography.titleMedium,
                        color = PortalColors.OnBackground,
                        modifier = Modifier.padding(top = SECTION_SPACING, bottom = 4.dp),
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        lazyItems(otherSeasons, key = { it.id.toString() }) { s ->
                            SeasonPoster(s, onClick = { onSeasonClick(s) })
                        }
                    }
                }
            }
        }
    }
}

/** Episode grid cell — thumbnail + "N. Name" + short description. */
@Composable
private fun EpisodeCard(episode: BaseItemDto, onClick: () -> Unit) {
    val apiClient: ApiClient = koinInject()
    val context = LocalContext.current
    val still = remember(apiClient, episode.id) {
        val url = apiClient.imageApi.getItemImageUrl(
            itemId = episode.id,
            imageType = ImageType.PRIMARY,
            tag = episode.imageTags?.get(ImageType.PRIMARY),
            maxWidth = EPISODE_IMG_PX,
        )
        ImageRequest.Builder(context).data(url).crossfade(true).build()
    }
    Column(modifier = Modifier.pressable(onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(PortalColors.SurfaceVariant),
        ) {
            AsyncImage(
                model = still,
                contentDescription = episode.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = episode.indexNumber?.let { "$it. ${episode.name.orEmpty()}" } ?: episode.name.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = PortalColors.OnBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        episode.overview?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = PortalColors.OnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Confirm dialog for a whole-season download. Shows episode count, estimated
 * size at the chosen quality, and free space. If it won't fit, it warns and (when
 * possible) offers the lowest quality that would; otherwise it offers each preset.
 */
@Composable
private fun SeasonDownloadDialog(
    plan: SeasonDownloadPlan,
    onConfirm: (org.jellyfin.mobile.downloads.DownloadQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download season") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${plan.episodeCount} episodes · ~${formatBytes(plan.estimatedBytes)} at " +
                        "${plan.quality.label}",
                    color = PortalColors.OnBackground,
                )
                Text(
                    "${formatBytes(plan.freeBytes)} free on device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PortalColors.OnSurface,
                )
                if (!plan.fits) {
                    val warn = plan.suggestLowerQuality?.let {
                        "Not enough space at ${plan.quality.label}. Try ${it.label} instead."
                    } ?: "Not enough free space for this season."
                    Text(warn, color = PortalColors.Error)
                }
            }
        },
        confirmButton = {
            // If it fits, confirm at the chosen quality; if a lower quality is
            // suggested, that's the primary action; otherwise only Cancel makes sense.
            when {
                plan.fits -> TextButton(onClick = { onConfirm(plan.quality) }) {
                    Text("Download ${plan.quality.label}")
                }
                plan.suggestLowerQuality != null -> TextButton(
                    onClick = { onConfirm(plan.suggestLowerQuality) },
                ) {
                    Text("Download ${plan.suggestLowerQuality.label}")
                }
                else -> {}
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024 * 1024)
    if (gb >= 1.0) return "%.1f GB".format(gb)
    val mb = bytes / (1024.0 * 1024)
    return "%.0f MB".format(mb)
}

@Composable
private fun SeasonPoster(season: BaseItemDto, onClick: () -> Unit) {
    val apiClient: ApiClient = koinInject()
    val context = LocalContext.current
    val img = remember(apiClient, season.id) {
        val url = apiClient.imageApi.getItemImageUrl(
            itemId = season.id,
            imageType = ImageType.PRIMARY,
            tag = season.imageTags?.get(ImageType.PRIMARY),
            maxWidth = SEASON_IMG_PX,
        )
        ImageRequest.Builder(context).data(url).crossfade(true).build()
    }
    Column(modifier = Modifier.width(SEASON_CARD_WIDTH).pressable(onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(PortalColors.SurfaceVariant),
        ) {
            AsyncImage(
                model = img,
                contentDescription = season.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = season.name.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = PortalColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
