package org.jellyfin.mobile.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jellyfin.mobile.ui.screens.pressable
import org.jellyfin.mobile.ui.screens.shimmer
import org.jellyfin.mobile.ui.utils.PortalColors
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.compose.koinInject

// Tuned for the Portal's 1280x800 landscape screen. Cards are landscape
// (16:9-ish) so episode stills and movie backdrops both look right, and the
// touch targets clear the 52dp Portal minimum comfortably.
private val CARD_WIDTH = 264.dp
private val EDGE_PADDING = 28.dp
private val ROW_SPACING = 26.dp
private val CARD_SPACING = 16.dp
private val CARD_CORNER = 12.dp

// Request/decode images at ~2x card width so they stay crisp on the Portal
// panel; height follows the 16:9 card.
private const val CARD_IMAGE_WIDTH_PX = 528
private const val CARD_IMAGE_HEIGHT_PX = 297

@Composable
fun HomeScreen(
    onItemClick: (BaseItemDto) -> Unit,
    onLibraryClick: (BaseItemDto) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    viewModel: HomeViewModel,
    topContentPadding: Dp = 0.dp,
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            is HomeState.Loading -> HomeSkeleton(topContentPadding)
            is HomeState.Empty -> Text(
                text = "Nothing to continue watching yet",
                color = PortalColors.OnSurface,
                modifier = Modifier.align(Alignment.Center),
            )
            is HomeState.Error -> Text(
                text = current.message,
                color = PortalColors.Error,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(EDGE_PADDING),
            )
            is HomeState.Content -> HomeContent(
                content = current,
                onItemClick = onItemClick,
                onLibraryClick = onLibraryClick,
                onSettingsClick = onSettingsClick,
                onSearchClick = onSearchClick,
                onDownloadsClick = onDownloadsClick,
                topContentPadding = topContentPadding,
            )
        }
    }
}

@Composable
private fun HomeContent(
    content: HomeState.Content,
    onItemClick: (BaseItemDto) -> Unit,
    onLibraryClick: (BaseItemDto) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    topContentPadding: Dp,
) {
    // A plain scrolling Column, NOT a LazyColumn. The home has only a handful of
    // rows; the expensive part is each row's LazyRow. In a LazyColumn, a whole
    // LazyRow re-composes the instant it scrolls into view — a burst of work in
    // one frame that caused the vertical-scroll jank (measured: Slow UI thread,
    // 40-200ms frames). With a Column, all rows compose once up front and
    // scrolling is pure translation with no per-row recomposition. The inner
    // LazyRows still keep their horizontal items lazy.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = topContentPadding + EDGE_PADDING, bottom = EDGE_PADDING),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
    ) {
        // My Media — the libraries as a normal row (server-provided thumbnail per
        // library), but with the library-card style so it reads as navigation.
        // Standard Jellyfin layout; scales when there are many libraries.
        if (content.libraries.isNotEmpty()) {
            HomeRowView(
                row = HomeRow("My Media", content.libraries),
                onItemClick = onLibraryClick,
                isLibraryRow = true,
                onSettingsClick = onSettingsClick,
                onSearchClick = onSearchClick,
                onDownloadsClick = onDownloadsClick,
            )
        }
        // Continue Watching, Next Up, New Releases (<library>) — see HomeViewModel.
        for (row in content.rows) {
            HomeRowView(row, onItemClick)
        }
    }
}

@Composable
private fun HomeRowView(
    row: HomeRow,
    onItemClick: (BaseItemDto) -> Unit,
    isLibraryRow: Boolean = false,
    onSettingsClick: (() -> Unit)? = null,
    onSearchClick: (() -> Unit)? = null,
    onDownloadsClick: (() -> Unit)? = null,
) {
    Column {
        RowTitle(
            row.title,
            onSearchClick = onSearchClick,
            onDownloadsClick = onDownloadsClick,
            onSettingsClick = onSettingsClick,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = EDGE_PADDING),
            horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
        ) {
            items(
                row.items.distinctBy { it.id },
                key = { item -> item.id.toString() },
                contentType = { if (isLibraryRow) "library-card" else "media-card" },
            ) { item ->
                if (isLibraryRow) {
                    LibraryCard(item = item, onClick = { onItemClick(item) })
                } else {
                    MediaCard(item = item, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

/**
 * Row title. The top ("My Media") row also carries right-aligned Search,
 * Downloads + Settings icon buttons — they live in scroll content (below the
 * OSD band) so they're actually tappable, unlike the dead header.
 */
@Composable
private fun RowTitle(
    title: String,
    onSearchClick: (() -> Unit)? = null,
    onDownloadsClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = EDGE_PADDING, end = EDGE_PADDING - 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = PortalColors.OnBackground,
        )
        if (onSearchClick != null || onDownloadsClick != null || onSettingsClick != null) {
            Spacer(Modifier.weight(1f))
            onSearchClick?.let {
                TitleAction(icon = Icons.Filled.Search, contentDescription = "Search", onClick = it)
            }
            onDownloadsClick?.let {
                TitleAction(icon = Icons.Filled.Download, contentDescription = "Downloads", onClick = it)
            }
            onSettingsClick?.let {
                TitleAction(icon = Icons.Filled.Settings, contentDescription = "Settings", onClick = it)
            }
        }
    }
}

/** 62dp icon button for the title-row actions (bumped ~20% for easier tapping). */
@Composable
private fun TitleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(62.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = PortalColors.OnBackground,
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
private fun MediaCard(
    item: BaseItemDto,
    onClick: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    val context = LocalContext.current

    val imageRequest = remember(apiClient, item.id, context) {
        val url = item.cardImageUrl(apiClient, maxWidth = CARD_IMAGE_WIDTH_PX)
        ImageRequest.Builder(context)
            .data(url)
            // Pin the decode to the card's pixel size so we don't hold/upload a
            // full-res bitmap per card — cuts the memory + GPU cost that drives
            // the vertical-scroll hitch.
            .size(CARD_IMAGE_WIDTH_PX, CARD_IMAGE_HEIGHT_PX)
            .crossfade(true)
            .build()
    }

    Column(modifier = Modifier.width(CARD_WIDTH).pressable(onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(CARD_CORNER))
                .background(PortalColors.SurfaceVariant),
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Resume progress bar — this rail is "Continue Watching".
            val played = item.userData?.playedPercentage
            if (played != null && played > 0.0) {
                LinearProgressIndicator(
                    progress = { (played / 100.0).toFloat().coerceIn(0f, 1f) },
                    color = PortalColors.MetaBlue,
                    trackColor = PortalColors.Background,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = cardLabel(item),
            style = MaterialTheme.typography.bodyMedium,
            color = PortalColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Library ("My Media") card. Jellyfin's library images already have the library
 * name rendered into the artwork (just like jellyfin-web's library cards), so we
 * show the image alone — no separate label, which would duplicate the name.
 */
@Composable
private fun LibraryCard(
    item: BaseItemDto,
    onClick: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    val context = LocalContext.current

    val imageRequest = remember(apiClient, item.id, context) {
        val url = item.cardImageUrl(apiClient, maxWidth = CARD_IMAGE_WIDTH_PX)
        ImageRequest.Builder(context)
            .data(url)
            .size(CARD_IMAGE_WIDTH_PX, CARD_IMAGE_HEIGHT_PX)
            .crossfade(true)
            .build()
    }

    Box(
        modifier = Modifier
            .width(CARD_WIDTH)
            .pressable(onClick)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(CARD_CORNER))
            .background(PortalColors.SurfaceVariant),
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Loading skeleton: placeholder rows with a sweeping shimmer. Mirrors the real
 * home geometry exactly — same EDGE_PADDING / ROW_SPACING / CARD_SPACING, the
 * same CARD_WIDTH 16:9 cards, and a label stub under each card (real cards have a
 * 6dp gap + a text line), so nothing shifts when content loads. The first row's
 * title also reserves space for the right-aligned action icons.
 */
@Composable
private fun HomeSkeleton(topContentPadding: Dp = 0.dp) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topContentPadding + EDGE_PADDING, bottom = EDGE_PADDING),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
    ) {
        repeat(3) { rowIndex ->
            Column {
                // Placeholder title row. Must match RowTitle's geometry EXACTLY so
                // cards don't shift when content loads: same padding, and on the
                // first row the action icons are 62dp boxes (the real IconButton
                // bounds) with a 34dp shimmer circle centered — this is what makes
                // the row 62dp tall, matching the loaded layout.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = EDGE_PADDING, end = EDGE_PADDING - 4.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmer(),
                    )
                    if (rowIndex == 0) {
                        Spacer(Modifier.weight(1f))
                        repeat(3) {
                            Box(
                                modifier = Modifier.size(62.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .shimmer(),
                                )
                            }
                        }
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = EDGE_PADDING),
                    horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
                    userScrollEnabled = false,
                ) {
                    items(5) {
                        Column(modifier = Modifier.width(CARD_WIDTH)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(CARD_CORNER))
                                    .shimmer(),
                            )
                            // Label stub — matches MediaCard's 6dp gap + text line.
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmer(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Episodes read better as "Series · S1:E2"; everything else uses its name. */
private fun cardLabel(item: BaseItemDto): String {
    val series = item.seriesName
    return if (!series.isNullOrEmpty()) {
        val season = item.parentIndexNumber
        val episode = item.indexNumber
        if (season != null && episode != null) {
            "$series · S$season:E$episode"
        } else {
            series
        }
    } else {
        item.name.orEmpty()
    }
}
