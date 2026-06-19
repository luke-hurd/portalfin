package org.jellyfin.mobile.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jellyfin.mobile.ui.screens.pressable
import org.jellyfin.mobile.ui.utils.PortalColors
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaStreamType
import org.koin.compose.koinInject

private val EDGE_PADDING = 32.dp
// +15dp over the old 16dp so the lower sections breathe.
private val SECTION_SPACING = 31.dp
// Where the content layer begins over the fullscreen background image — far
// enough down that the title sits on the image's faded lower region.
private val CONTENT_TOP_OFFSET = 320.dp
private val CAST_PHOTO = 115.dp // +20%
private val TITLE_ART_MAX_HEIGHT = 96.dp
private const val TITLE_ART_WIDTH_FRACTION = 0.35f
private const val TITLE_ART_WIDTH_PX = 600
private const val BACKDROP_WIDTH_PX = 1280
private const val CAST_IMG_PX = 240
private const val TICKS_PER_MINUTE = 600_000_000L
private const val MAX_CAST = 20
private val CHAPTER_CARD_WIDTH = 200.dp
private const val CHAPTER_IMG_PX = 400
private val RELATED_CARD_WIDTH = 130.dp
private const val RELATED_IMG_PX = 300
private val SUBTITLE_MAX_WIDTH = 200.dp
private const val SUBTITLE_MAX_CHARS = 100
private val EPISODE_STILL_WIDTH = 360.dp

@Composable
fun DetailScreen(
    onPlay: (item: BaseItemDto, startTicks: Long, subtitleIndex: Int?) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    onOpenDownloads: () -> Unit,
    viewModel: DetailViewModel,
    topContentPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val state by viewModel.state.collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()

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
            is DetailState.Content -> DetailContent(
                content = current,
                onPlay = onPlay,
                onItemClick = onItemClick,
                topContentPadding = topContentPadding,
                downloadStatus = downloadStatus,
                onDownload = { quality -> viewModel.download(current.item, quality) },
                onOpenDownloads = onOpenDownloads,
            )
        }
    }
}

@Composable
private fun DetailContent(
    content: DetailState.Content,
    onPlay: (BaseItemDto, Long, Int?) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    topContentPadding: androidx.compose.ui.unit.Dp,
    downloadStatus: org.jellyfin.mobile.downloads.DownloadStatus?,
    onDownload: (org.jellyfin.mobile.downloads.DownloadQuality) -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val item = content.item
    val apiClient: ApiClient = koinInject()
    val context = LocalContext.current

    // Background image. For an EPISODE use the SERIES/parent backdrop (the episode
    // still is shown separately on top, so don't double it up here). Otherwise the
    // item's own wide BACKDROP, falling back to PRIMARY.
    val hero = remember(apiClient, item.id, context) {
        val (bgId, bgType) = when {
            item.type == BaseItemKind.EPISODE && item.parentBackdropItemId != null ->
                item.parentBackdropItemId!! to ImageType.BACKDROP
            item.type == BaseItemKind.EPISODE && item.seriesId != null ->
                item.seriesId!! to ImageType.BACKDROP
            !item.backdropImageTags.isNullOrEmpty() -> item.id to ImageType.BACKDROP
            else -> item.id to ImageType.PRIMARY
        }
        val url = apiClient.imageApi.getItemImageUrl(
            itemId = bgId,
            imageType = bgType,
            maxWidth = BACKDROP_WIDTH_PX,
        )
        ImageRequest.Builder(context).data(url).crossfade(true).build()
    }

    // TWO LAYERS in ONE scroll container (see memory: detail-hero-spec):
    //  - BACKGROUND: the backdrop renders FULLSCREEN at rest (full screen height),
    //    top-aligned + horizontally centered (Crop + TopCenter → only the sides
    //    clip, never the top), fading out at its bottom into the page background.
    //  - CONTENT: sits ON TOP and scrolls OVER the image.
    // Both scroll together. Resizing the image does NOT move the content.
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Background image layer — fullscreen, top-aligned, fades out at the bottom.
        Box(modifier = Modifier.fillMaxWidth().height(screenHeight)) {
            AsyncImage(
                model = hero,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        // Raised fade: stays clear up top, ramps to a solid dark
                        // background by ~80% so text/buttons below sit on dark, not
                        // on the bright lower part of the image.
                        val brush = Brush.verticalGradient(
                            0.25f to Color.Transparent,
                            0.6f to PortalColors.Background.copy(alpha = 0.75f),
                            0.8f to PortalColors.Background,
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(brush)
                        }
                    },
            )
        }

        // Content layer on top — pushed down so it begins over the image's lower,
        // faded region; scrolls up over the background.
        DetailInfo(
            content = content,
            onPlay = onPlay,
            onItemClick = onItemClick,
            downloadStatus = downloadStatus,
            onDownload = onDownload,
            onOpenDownloads = onOpenDownloads,
            modifier = Modifier.padding(top = CONTENT_TOP_OFFSET),
        )
    }
}

@Composable
private fun DetailInfo(
    content: DetailState.Content,
    onPlay: (BaseItemDto, Long, Int?) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    downloadStatus: org.jellyfin.mobile.downloads.DownloadStatus?,
    onDownload: (org.jellyfin.mobile.downloads.DownloadQuality) -> Unit,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = content.item
    val isSeries = item.type == BaseItemKind.SERIES
    val isEpisode = item.type == BaseItemKind.EPISODE
    val subtitles = remember(item) {
        item.mediaStreams.orEmpty().filter { it.type == MediaStreamType.SUBTITLE }
    }
    var subtitleIndex by remember(item) { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = EDGE_PADDING)
            .padding(horizontal = EDGE_PADDING),
        verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
    ) {
        if (isEpisode) {
            // Episode: still image above a text title (series · SxxExx · name).
            EpisodeStill(item)
            EpisodeTitle(item)
        } else {
            TitleArt(item)
        }

        item.taglines?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { tagline ->
            Text(
                text = tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = PortalColors.OnSurface,
            )
        }

        MetaRow(item)

        if (isSeries) {
            // Series: play the next-up episode (resume where you left off), or the
            // first episode if unwatched. Episode-level subtitles/chapters live on
            // the episode detail, not here.
            SeriesPlayAction(content.nextUp, onPlay = { ep, ticks -> onPlay(ep, ticks, null) })
        } else {
            // Play/Resume/Start Over + the subtitle picker + download on one row.
            PlayActions(item, onPlay = { startTicks -> onPlay(item, startTicks, subtitleIndex) }) {
                if (subtitles.isNotEmpty()) {
                    SubtitlePicker(subtitles, subtitleIndex) { subtitleIndex = it }
                }
                DownloadAction(
                    status = downloadStatus,
                    onDownload = onDownload,
                    onOpenDownloads = onOpenDownloads,
                )
            }
        }

        item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyLarge,
                color = PortalColors.OnBackground,
            )
        }

        item.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
            GenreChips(genres)
        }

        when {
            isSeries -> if (content.seasons.isNotEmpty()) SeasonsRow(content.seasons, onItemClick)
            // Episode: other episodes in the season, under the description / above cast.
            isEpisode -> if (content.siblingEpisodes.isNotEmpty()) {
                EpisodeStripRow(content.siblingEpisodes, currentId = item.id, onItemClick = onItemClick)
            }
            else -> item.chapters?.takeIf { it.isNotEmpty() }?.let { chapters ->
                ChapterRow(item, chapters) { startTicks -> onPlay(item, startTicks, subtitleIndex) }
            }
        }

        item.people?.filter { !it.name.isNullOrBlank() }?.takeIf { it.isNotEmpty() }?.let { people ->
            CastRow(people.take(MAX_CAST))
        }

        if (content.similar.isNotEmpty()) {
            RelatedRow(content.similar, onItemClick)
        }
    }
}

/** Series play button: "Resume SxxExx · dur" from the next-up episode, else "Play S1E1". */
@Composable
private fun SeriesPlayAction(nextUp: BaseItemDto?, onPlay: (BaseItemDto, Long) -> Unit) {
    if (nextUp == null) return // still loading or no episodes
    val resumeTicks = nextUp.userData?.playbackPositionTicks ?: 0L
    val season = nextUp.parentIndexNumber
    val episode = nextUp.indexNumber
    val epLabel = if (season != null && episode != null) {
        "S%02dE%02d".format(season, episode)
    } else {
        nextUp.name.orEmpty()
    }
    val verb = if (resumeTicks > 0) "Resume" else "Play"
    val durSuffix = if (resumeTicks > 0) "  ·  ${formatTicks(resumeTicks)}" else ""
    Button(
        onClick = { onPlay(nextUp, resumeTicks) },
        colors = ButtonDefaults.buttonColors(containerColor = PortalColors.MetaBlue),
        modifier = Modifier.heightIn(min = 52.dp).widthIn(min = 200.dp),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null)
        Text(text = "  $verb $epLabel$durSuffix", style = MaterialTheme.typography.labelLarge)
    }
}

/** Seasons poster row — tap a season to open its episode list. */
@Composable
private fun SeasonsRow(seasons: List<BaseItemDto>, onItemClick: (BaseItemDto) -> Unit) {
    val apiClient: ApiClient = koinInject()
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Seasons", style = MaterialTheme.typography.titleMedium, color = PortalColors.OnBackground)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(seasons.distinctBy { it.id }, key = { it.id.toString() }) { season ->
                val img = remember(apiClient, season.id) {
                    val url = apiClient.imageApi.getItemImageUrl(
                        itemId = season.id,
                        imageType = ImageType.PRIMARY,
                        tag = season.imageTags?.get(ImageType.PRIMARY),
                        maxWidth = RELATED_IMG_PX,
                    )
                    ImageRequest.Builder(context).data(url).crossfade(true).build()
                }
                Column(modifier = Modifier.width(RELATED_CARD_WIDTH).pressable { onItemClick(season) }) {
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
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = season.name.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PortalColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Graphical title (Logo image) when the item has one — left-justified, capped at
 * 35% of screen width so it stays on the left. Falls back to the text title.
 */
@Composable
private fun TitleArt(item: BaseItemDto) {
    val apiClient: ApiClient = koinInject()
    val context = LocalContext.current
    val logoTag = item.imageTags?.get(ImageType.LOGO)

    if (logoTag != null) {
        val maxWidth = LocalConfiguration.current.screenWidthDp.dp * TITLE_ART_WIDTH_FRACTION
        val logo = remember(apiClient, item.id, logoTag) {
            val url = apiClient.imageApi.getItemImageUrl(
                itemId = item.id,
                imageType = ImageType.LOGO,
                tag = logoTag,
                maxWidth = TITLE_ART_WIDTH_PX,
            )
            ImageRequest.Builder(context).data(url).crossfade(true).build()
        }
        AsyncImage(
            model = logo,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier
                .heightIn(max = TITLE_ART_MAX_HEIGHT)
                .widthIn(max = maxWidth),
        )
    } else {
        Text(
            text = item.name.orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
        )
    }
}

/** Episode still — 16:9 image shown above the title on an episode detail page. */
@Composable
private fun EpisodeStill(item: BaseItemDto) {
    val apiClient: ApiClient = koinInject()
    val context = LocalContext.current
    val still = remember(apiClient, item.id) {
        val url = apiClient.imageApi.getItemImageUrl(
            itemId = item.id,
            imageType = ImageType.PRIMARY,
            tag = item.imageTags?.get(ImageType.PRIMARY),
            maxWidth = BACKDROP_WIDTH_PX,
        )
        ImageRequest.Builder(context).data(url).crossfade(true).build()
    }
    Box(
        modifier = Modifier
            .width(EPISODE_STILL_WIDTH)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp))
            .background(PortalColors.SurfaceVariant),
    ) {
        AsyncImage(
            model = still,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Episode title: "Series · SxxExx · Name" (text, since episodes have no logo art). */
@Composable
private fun EpisodeTitle(item: BaseItemDto) {
    val season = item.parentIndexNumber
    val episode = item.indexNumber
    val code = if (season != null && episode != null) "S%02dE%02d".format(season, episode) else null
    val prefix = listOfNotNull(item.seriesName, code).joinToString("  ·  ")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (prefix.isNotEmpty()) {
            Text(text = prefix, style = MaterialTheme.typography.bodyMedium, color = PortalColors.OnSurface)
        }
        Text(
            text = item.name.orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
        )
    }
}

/** Other episodes in the season — horizontal still row (current one excluded). */
@Composable
private fun EpisodeStripRow(
    episodes: List<BaseItemDto>,
    currentId: java.util.UUID,
    onItemClick: (BaseItemDto) -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    val context = LocalContext.current
    val others = remember(episodes, currentId) { episodes.filter { it.id != currentId } }
    if (others.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("More from this season", style = MaterialTheme.typography.titleMedium, color = PortalColors.OnBackground)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(others.distinctBy { it.id }, key = { it.id.toString() }) { ep ->
                val img = remember(apiClient, ep.id) {
                    val url = apiClient.imageApi.getItemImageUrl(
                        itemId = ep.id,
                        imageType = ImageType.PRIMARY,
                        tag = ep.imageTags?.get(ImageType.PRIMARY),
                        maxWidth = CHAPTER_IMG_PX,
                    )
                    ImageRequest.Builder(context).data(url).crossfade(true).build()
                }
                Column(modifier = Modifier.width(CHAPTER_CARD_WIDTH).pressable { onItemClick(ep) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PortalColors.SurfaceVariant),
                    ) {
                        AsyncImage(
                            model = img,
                            contentDescription = ep.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = ep.indexNumber?.let { "$it. ${ep.name.orEmpty()}" } ?: ep.name.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = PortalColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Genres as display-only M3 chips. Not clickable yet (diving into a genre is a
 * roadmap item) — so they're SuggestionChips with interaction disabled rather
 * than buttons that look tappable but do nothing.
 */
@Composable
private fun GenreChips(genres: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        genres.forEach { genre ->
            SuggestionChip(
                onClick = {},
                enabled = false,
                label = { Text(genre) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    disabledLabelColor = PortalColors.OnSurface,
                ),
                border = SuggestionChipDefaults.suggestionChipBorder(enabled = false),
                modifier = Modifier.heightIn(min = 44.dp),
            )
        }
    }
}

/** Cast & crew — circular headshots with name + role, scrolls horizontally. */
@Composable
private fun CastRow(people: List<BaseItemPerson>) {
    val apiClient: ApiClient = koinInject()
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Cast & Crew",
            style = MaterialTheme.typography.titleMedium,
            color = PortalColors.OnBackground,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // A person can appear twice (e.g. writer + presenter) — same id would
            // crash LazyRow's unique-key check, so dedupe by id.
            items(people.distinctBy { it.id }, key = { it.id.toString() }) { person ->
                val img = remember(apiClient, person.id) {
                    person.primaryImageTag?.let {
                        val url = apiClient.imageApi.getItemImageUrl(
                            itemId = person.id,
                            imageType = ImageType.PRIMARY,
                            tag = it,
                            maxWidth = CAST_IMG_PX,
                        )
                        ImageRequest.Builder(context).data(url).crossfade(true).build()
                    }
                }
                Column(
                    modifier = Modifier.width(CAST_PHOTO),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(CAST_PHOTO)
                            .clip(CircleShape)
                            .background(PortalColors.SurfaceVariant),
                    ) {
                        if (img != null) {
                            AsyncImage(
                                model = img,
                                contentDescription = person.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = person.name.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PortalColors.OnBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    person.role?.takeIf { it.isNotBlank() }?.let { role ->
                        Text(
                            text = role,
                            style = MaterialTheme.typography.bodySmall,
                            color = PortalColors.OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Play actions. With saved progress: "Resume HH:MM" (primary) + "Start Over".
 * Otherwise a single "Play". All M3, heightIn(min=52dp) per guide. onPlay carries
 * the start position in ticks (0 = from the beginning).
 */
@Composable
private fun PlayActions(
    item: BaseItemDto,
    onPlay: (Long) -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    val resumeTicks = item.userData?.playbackPositionTicks ?: 0L
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (resumeTicks > 0) {
            Button(
                onClick = { onPlay(resumeTicks) },
                colors = ButtonDefaults.buttonColors(containerColor = PortalColors.MetaBlue),
                modifier = Modifier.heightIn(min = 52.dp).widthIn(min = 160.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(text = "  Resume ${formatTicks(resumeTicks)}", style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = { onPlay(0L) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PortalColors.Surface,
                    contentColor = PortalColors.OnBackground,
                ),
                modifier = Modifier.heightIn(min = 52.dp),
            ) {
                Text(text = "Start Over", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Button(
                onClick = { onPlay(0L) },
                colors = ButtonDefaults.buttonColors(containerColor = PortalColors.MetaBlue),
                modifier = Modifier.heightIn(min = 52.dp).widthIn(min = 160.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(text = "  Play", style = MaterialTheme.typography.labelLarge)
            }
        }
        trailing()
    }
}

/**
 * Round download button for the action row, three states:
 * - not downloaded: download glyph; tap opens a quality menu.
 * - downloading/queued: spinner; tap opens the Downloads view (to watch progress).
 * - downloaded: check glyph on a blue-stroked circle; tap opens the Downloads view.
 * 56dp circle so it clears the Portal touch-target minimum.
 */
@Composable
private fun DownloadAction(
    status: org.jellyfin.mobile.downloads.DownloadStatus?,
    onDownload: (org.jellyfin.mobile.downloads.DownloadQuality) -> Unit,
    onOpenDownloads: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val downloading = status == org.jellyfin.mobile.downloads.DownloadStatus.QUEUED ||
        status == org.jellyfin.mobile.downloads.DownloadStatus.DOWNLOADING
    val done = status == org.jellyfin.mobile.downloads.DownloadStatus.DOWNLOADED

    Box {
        Surface(
            shape = CircleShape,
            color = PortalColors.Surface,
            // Blue stroke once downloaded so it reads as "saved".
            border = if (done) BorderStroke(2.dp, PortalColors.MetaBlue) else null,
            modifier = Modifier.size(56.dp),
        ) {
            Box(
                // Tapping while downloading OR downloaded jumps to the Downloads
                // view; otherwise open the quality menu.
                modifier = Modifier.pressable {
                    if (downloading || done) onOpenDownloads() else menuOpen = true
                },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    downloading -> CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 3.dp,
                        color = PortalColors.MetaBlue,
                    )
                    done -> Icon(
                        Icons.Filled.DownloadDone,
                        contentDescription = "Downloaded — open Downloads",
                        tint = PortalColors.MetaBlue,
                    )
                    else -> Icon(
                        Icons.Filled.Download,
                        contentDescription = "Download",
                        tint = PortalColors.OnBackground,
                    )
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            org.jellyfin.mobile.downloads.DownloadQuality.entries.forEach { quality ->
                DropdownMenuItem(
                    text = { Text("Download · ${quality.label}") },
                    onClick = {
                        menuOpen = false
                        onDownload(quality)
                    },
                )
            }
        }
    }
}

/** Meta line: year · runtime · age rating · ★ community · 🍅 critic · Ends at ~TIME. */
@Composable
private fun MetaRow(item: BaseItemDto) {
    val parts = buildList {
        item.productionYear?.let { add(it.toString()) }
        runtimeLabel(item)?.let { add(it) }
        item.officialRating?.let { add(it) }
        item.communityRating?.let { add("★ %.1f".format(it)) }
        item.criticRating?.let { add("🍅 ${it.toInt()}%") }
        endsAtLabel(item)?.let { add(it) }
    }
    if (parts.isNotEmpty()) {
        Text(
            text = parts.joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium,
            color = PortalColors.OnSurface,
        )
    }
}

/** "Ends at ~8:42 PM" — now + remaining runtime (accounts for resume position). */
@Composable
private fun endsAtLabel(item: BaseItemDto): String? {
    val total = item.runTimeTicks ?: return null
    val remaining = (total - (item.userData?.playbackPositionTicks ?: 0L)).coerceAtLeast(0L)
    val endMillis = System.currentTimeMillis() + remaining / 10_000L
    val time = remember(endMillis / 60_000L) {
        android.text.format.DateFormat.format("h:mm a", endMillis).toString()
    }
    return "Ends at ~$time"
}

/** Subtitle dropdown — applied at play time via PlayOptions.subtitleStreamIndex. */
@Composable
private fun SubtitlePicker(
    subtitles: List<org.jellyfin.sdk.model.api.MediaStream>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = subtitles.firstOrNull { it.index == selectedIndex }?.displayTitle ?: "Off"
    Box {
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = PortalColors.Surface,
                contentColor = PortalColors.OnBackground,
            ),
            // Cap width — some subtitle titles are long and would blow out the row.
            modifier = Modifier.heightIn(min = 52.dp).widthIn(max = SUBTITLE_MAX_WIDTH),
        ) {
            Text(
                text = "Subtitles: $label",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Off") }, onClick = { onSelect(null); expanded = false })
            subtitles.forEach { sub ->
                val name = (sub.displayTitle ?: sub.language ?: "Subtitle ${sub.index}").ellipsize(SUBTITLE_MAX_CHARS)
                DropdownMenuItem(
                    text = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onSelect(sub.index); expanded = false },
                )
            }
        }
    }
}

private fun String.ellipsize(max: Int): String = if (length <= max) this else take(max - 1).trimEnd() + "…"

/** Scene/chapter picker — horizontal row of chapter thumbnails; tap to play from there. */
@Composable
private fun ChapterRow(
    item: BaseItemDto,
    chapters: List<org.jellyfin.sdk.model.api.ChapterInfo>,
    onPlayFrom: (Long) -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Scenes", style = MaterialTheme.typography.titleMedium, color = PortalColors.OnBackground)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(chapters) { index, chapter ->
                val img = remember(apiClient, item.id, index) {
                    if (chapter.imageTag != null) {
                        val url = apiClient.imageApi.getItemImageUrl(
                            itemId = item.id,
                            imageType = ImageType.CHAPTER,
                            imageIndex = index,
                            tag = chapter.imageTag,
                            maxWidth = CHAPTER_IMG_PX,
                        )
                        ImageRequest.Builder(context).data(url).crossfade(true).build()
                    } else {
                        null
                    }
                }
                Column(
                    modifier = Modifier
                        .width(CHAPTER_CARD_WIDTH)
                        .pressable { onPlayFrom(chapter.startPositionTicks) },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PortalColors.SurfaceVariant),
                    ) {
                        if (img != null) {
                            AsyncImage(
                                model = img,
                                contentDescription = chapter.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = chapter.name ?: "Scene ${index + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PortalColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** "More Like This" — horizontal row of similar items. */
@Composable
private fun RelatedRow(similar: List<BaseItemDto>, onItemClick: (BaseItemDto) -> Unit) {
    val apiClient: ApiClient = koinInject()
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("More Like This", style = MaterialTheme.typography.titleMedium, color = PortalColors.OnBackground)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(similar.distinctBy { it.id }, key = { it.id.toString() }) { related ->
                val img = remember(apiClient, related.id) {
                    val url = apiClient.imageApi.getItemImageUrl(
                        itemId = related.id,
                        imageType = ImageType.PRIMARY,
                        tag = related.imageTags?.get(ImageType.PRIMARY),
                        maxWidth = RELATED_IMG_PX,
                    )
                    ImageRequest.Builder(context).data(url).crossfade(true).build()
                }
                Column(modifier = Modifier.width(RELATED_CARD_WIDTH).pressable { onItemClick(related) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PortalColors.SurfaceVariant),
                    ) {
                        AsyncImage(
                            model = img,
                            contentDescription = related.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = related.name.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = PortalColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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

private fun runtimeLabel(item: BaseItemDto): String? {
    val ticks = item.runTimeTicks ?: return null
    val totalMinutes = (ticks / TICKS_PER_MINUTE).toInt()
    if (totalMinutes <= 0) return null
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
