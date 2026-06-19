package org.jellyfin.mobile.ui.screens.search

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
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
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.koin.compose.koinInject

// Matches the library grid so search results feel like the same app.
private val EDGE_PADDING = 28.dp
private const val GRID_COLUMNS = 6
private val GRID_H_SPACING = 14.dp
private val GRID_V_SPACING = 18.dp
private val POSTER_CORNER = 10.dp
private const val POSTER_ASPECT = 2f / 3f
private const val POSTER_WIDTH_PX = 360
private const val POSTER_HEIGHT_PX = 540
private const val SKELETON_COUNT = 12

@Composable
fun SearchScreen(
    onItemClick: (BaseItemDto) -> Unit,
    viewModel: SearchViewModel,
    topContentPadding: Dp = 0.dp,
) {
    val state by viewModel.state.collectAsState()
    var query by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Drop the keyboard + field focus before opening an item, so the soft
    // keyboard doesn't linger on the detail page behind it.
    fun openItem(item: BaseItemDto) {
        keyboardController?.hide()
        focusManager.clearFocus()
        onItemClick(item)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = EDGE_PADDING,
            end = EDGE_PADDING,
            top = topContentPadding + EDGE_PADDING,
            bottom = EDGE_PADDING,
        ),
        horizontalArrangement = Arrangement.spacedBy(GRID_H_SPACING),
        verticalArrangement = Arrangement.spacedBy(GRID_V_SPACING),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "search-field") {
            SearchField(
                query = query,
                onQueryChange = {
                    query = it
                    viewModel.onQueryChange(it)
                },
                onClear = {
                    query = ""
                    viewModel.onQueryChange("")
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        when (val current = state) {
            is SearchState.Idle ->
                messageCell("Search your library for movies, shows and more")
            is SearchState.Loading -> skeletonCells()
            is SearchState.Empty ->
                messageCell("No results for “${current.term}”")
            is SearchState.Error -> messageCell(current.message)
            is SearchState.Results ->
                items(
                    current.items.distinctBy { it.id },
                    key = { it.id.toString() },
                    contentType = { "poster" },
                ) { item ->
                    PosterCard(item = item, onClick = { openItem(item) })
                }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        placeholder = { Text("Search") },
        singleLine = true,
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = PortalColors.OnSurface)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = PortalColors.OnSurface)
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PortalColors.MetaBlue,
            cursorColor = PortalColors.MetaBlue,
        ),
    )
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.messageCell(message: String) {
    item(span = { GridItemSpan(maxLineSpan) }, contentType = "message") {
        Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
            Text(text = message, color = PortalColors.OnSurface, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.skeletonCells() {
    items(SKELETON_COUNT, contentType = { "poster-skeleton" }) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(POSTER_ASPECT)
                    .clip(RoundedCornerShape(POSTER_CORNER))
                    .shimmer(),
            )
            Spacer(modifier = Modifier.height(6.dp))
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

@Composable
private fun PosterCard(item: BaseItemDto, onClick: () -> Unit) {
    val apiClient: ApiClient = koinInject()
    val context = LocalContext.current

    val imageRequest = remember(apiClient, item.id, context) {
        val url = apiClient.imageApi.getItemImageUrl(
            itemId = item.id,
            imageType = ImageType.PRIMARY,
            tag = item.imageTags?.get(ImageType.PRIMARY),
            maxWidth = POSTER_WIDTH_PX,
        )
        ImageRequest.Builder(context)
            .data(url)
            .size(POSTER_WIDTH_PX, POSTER_HEIGHT_PX)
            .crossfade(true)
            .build()
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(POSTER_CORNER))
            .pressable(onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(POSTER_ASPECT)
                .clip(RoundedCornerShape(POSTER_CORNER))
                .background(PortalColors.SurfaceVariant),
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.name.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = PortalColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
