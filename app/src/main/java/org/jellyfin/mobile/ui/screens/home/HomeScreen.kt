package org.jellyfin.mobile.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jellyfin.mobile.ui.utils.PortalColors
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.compose.koinInject

// Tuned for the Portal's 1280x800 landscape screen. Cards are landscape
// (16:9-ish) so episode stills and movie backdrops both look right, and the
// touch targets clear the 52dp Portal minimum comfortably.
private val CARD_WIDTH = 220.dp
private val EDGE_PADDING = 24.dp

// Request/decode images at ~2x card width so they stay crisp on the Portal
// panel; height follows the 16:9 card.
private const val CARD_IMAGE_WIDTH_PX = 440
private const val CARD_IMAGE_HEIGHT_PX = 248

@Composable
fun HomeScreen(
    onItemClick: (BaseItemDto) -> Unit,
    onLibraryClick: (BaseItemDto) -> Unit,
    viewModel: HomeViewModel,
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            is HomeState.Loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
            )
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
            is HomeState.Content -> HomeContent(current, onItemClick, onLibraryClick)
        }
    }
}

@Composable
private fun HomeContent(
    content: HomeState.Content,
    onItemClick: (BaseItemDto) -> Unit,
    onLibraryClick: (BaseItemDto) -> Unit,
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
            .padding(vertical = EDGE_PADDING),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (content.libraries.isNotEmpty()) {
            CategoryBar(libraries = content.libraries, onLibraryClick = onLibraryClick)
        }
        for (row in content.rows) {
            HomeRowView(row, onItemClick)
        }
    }
}

/** Top-level library chips (Movies, TV Shows, …). Tap to dive into a library. */
@Composable
private fun CategoryBar(
    libraries: List<BaseItemDto>,
    onLibraryClick: (BaseItemDto) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = EDGE_PADDING),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(libraries, key = { it.id.toString() }) { library ->
            Text(
                text = library.name.orEmpty(),
                style = MaterialTheme.typography.button,
                color = PortalColors.OnBackground,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onLibraryClick(library) }
                    .background(PortalColors.Surface)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun HomeRowView(
    row: HomeRow,
    onItemClick: (BaseItemDto) -> Unit,
) {
    Column {
        Text(
            text = row.title,
            style = MaterialTheme.typography.h6,
            color = PortalColors.OnBackground,
            modifier = Modifier.padding(start = EDGE_PADDING, bottom = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = EDGE_PADDING),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(
                row.items,
                key = { item -> item.id.toString() },
                contentType = { "media-card" },
            ) { item ->
                MediaCard(item = item, onClick = { onItemClick(item) })
            }
        }
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

    Column(
        modifier = Modifier
            .width(CARD_WIDTH)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
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
                    progress = (played / 100.0).toFloat().coerceIn(0f, 1f),
                    color = PortalColors.MetaBlue,
                    backgroundColor = PortalColors.Background,
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
            style = MaterialTheme.typography.body2,
            color = PortalColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
