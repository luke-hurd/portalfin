package org.jellyfin.mobile.ui.screens.detail

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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

@Composable
fun DetailScreen(
    onPlay: (item: BaseItemDto, startTicks: Long, subtitleIndex: Int?) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
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
            is DetailState.Content -> DetailContent(current, onPlay, onItemClick, topContentPadding)
        }
    }
}

@Composable
private fun DetailContent(
    content: DetailState.Content,
    onPlay: (BaseItemDto, Long, Int?) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    topContentPadding: androidx.compose.ui.unit.Dp,
) {
    val item = content.item
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
            modifier = Modifier.padding(top = CONTENT_TOP_OFFSET),
        )
    }
}

@Composable
private fun DetailInfo(
    content: DetailState.Content,
    onPlay: (BaseItemDto, Long, Int?) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = content.item
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
        TitleArt(item)

        item.taglines?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { tagline ->
            Text(
                text = tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = PortalColors.OnSurface,
            )
        }

        MetaRow(item)

        // Play/Resume/Start Over + the subtitle picker on one row.
        PlayActions(item, onPlay = { startTicks -> onPlay(item, startTicks, subtitleIndex) }) {
            if (subtitles.isNotEmpty()) {
                SubtitlePicker(subtitles, subtitleIndex) { subtitleIndex = it }
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

        item.chapters?.takeIf { it.isNotEmpty() }?.let { chapters ->
            ChapterRow(item, chapters) { startTicks -> onPlay(item, startTicks, subtitleIndex) }
        }

        item.people?.filter { !it.name.isNullOrBlank() }?.takeIf { it.isNotEmpty() }?.let { people ->
            CastRow(people.take(MAX_CAST))
        }

        if (content.similar.isNotEmpty()) {
            RelatedRow(content.similar, onItemClick)
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

/** Genres as M3 chips. */
@Composable
private fun GenreChips(genres: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        genres.forEach { genre ->
            AssistChip(
                onClick = { /* TODO: dive into genre */ },
                label = { Text(genre) },
                modifier = Modifier.heightIn(min = 52.dp),
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
            items(people, key = { it.id.toString() }) { person ->
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
            items(similar, key = { it.id.toString() }) { related ->
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
