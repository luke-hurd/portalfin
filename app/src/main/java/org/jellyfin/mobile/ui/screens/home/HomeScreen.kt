package org.jellyfin.mobile.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
) {
    Column {
        RowTitle(row.title)
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
            // Settings card trails the My Media libraries — half width, big gear.
            // Lives in scroll content (below the OSD band) so it's actually tappable,
            // unlike the dead header.
            if (onSettingsClick != null) {
                item(contentType = "settings-card") {
                    SettingsCard(onClick = onSettingsClick)
                }
            }
        }
    }
}

/** Half-width card (same height as a library card) with a large gear; opens Settings. */
@Composable
private fun SettingsCard(onClick: () -> Unit) {
    // Library cards are CARD_WIDTH wide at 16:9 → this is half the width, same height.
    val cardHeight = CARD_WIDTH * 9f / 16f
    Box(
        modifier = Modifier
            .width(CARD_WIDTH / 2)
            .height(cardHeight)
            .clip(RoundedCornerShape(CARD_CORNER))
            .background(PortalColors.Surface)
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = "Settings",
            tint = PortalColors.OnBackground,
            modifier = Modifier.size(48.dp),
        )
    }
}

@Composable
private fun RowTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = PortalColors.OnBackground,
        modifier = Modifier.padding(start = EDGE_PADDING, bottom = 12.dp),
    )
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

/** Loading skeleton: placeholder rows with a sweeping shimmer (see Modifier.shimmer). */
@Composable
private fun HomeSkeleton(topContentPadding: Dp = 0.dp) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topContentPadding + EDGE_PADDING, bottom = EDGE_PADDING),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
    ) {
        repeat(3) {
            Column {
                // Placeholder row title.
                Box(
                    modifier = Modifier
                        .padding(start = EDGE_PADDING, bottom = 12.dp)
                        .width(180.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmer(),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = EDGE_PADDING),
                    horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
                    userScrollEnabled = false,
                ) {
                    items(5) {
                        Box(
                            modifier = Modifier
                                .width(CARD_WIDTH)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(CARD_CORNER))
                                .shimmer(),
                        )
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
