package org.jellyfin.mobile.ui.screens.ambient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * A single backdrop to show in the ambient slideshow, with an optional title-art
 * logo (same Logo image the detail page uses). [logoUrl] is null when the item
 * has no logo art uploaded — the screen falls back to the text [title] then.
 */
data class AmbientSlide(
    val backdropUrl: String,
    val title: String,
    val year: Int?,
    val logoUrl: String?,
)

/**
 * Supplies the native ambient screensaver with a shuffled set of library
 * backdrops. Mirrors the old restyle.js ambient fetch, but native: random Movies
 * + Series that actually have a Backdrop, addressed through the SDK's image API
 * (the injected [ApiClient] already carries the server URL + access token, so no
 * auth wiring is needed — same as HomeViewModel / ImageProvider).
 */
class AmbientViewModel : ViewModel(), KoinComponent {
    private val apiClient: ApiClient by inject()

    private val _slides = MutableStateFlow<List<AmbientSlide>>(emptyList())
    val slides: StateFlow<List<AmbientSlide>> = _slides.asStateFlow()

    private var loading = false

    /**
     * Fetch backdrops. Call every time the screensaver engages: if we already have
     * slides we keep them (no flicker), but if we're empty — e.g. the first fetch
     * ran before login, or the server was briefly unreachable — we retry instead of
     * showing a blank screensaver. [force] re-fetches even when slides exist.
     */
    fun load(force: Boolean = false) {
        if (loading) return
        if (_slides.value.isNotEmpty() && !force) return
        loading = true
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                try {
                    fetchSlides()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load ambient backdrops")
                    emptyList()
                }
            }
            if (items.isNotEmpty()) _slides.value = items
            loading = false
        }
    }

    private suspend fun fetchSlides(): List<AmbientSlide> {
        val result by apiClient.itemsApi.getItems(
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
            recursive = true,
            sortBy = listOf(ItemSortBy.RANDOM),
            imageTypeLimit = 1,
            enableImageTypes = listOf(ImageType.BACKDROP, ImageType.LOGO),
            limit = SLIDE_LIMIT,
        )

        return result.items.orEmpty().mapNotNull { item ->
            val backdropTag = item.backdropImageTags?.firstOrNull() ?: return@mapNotNull null
            val backdropUrl = apiClient.imageApi.getItemImageUrl(
                itemId = item.id,
                imageType = ImageType.BACKDROP,
                tag = backdropTag,
                maxWidth = BACKDROP_MAX_WIDTH,
            )
            val logoTag = item.imageTags?.get(ImageType.LOGO)
            val logoUrl = logoTag?.let { tag ->
                apiClient.imageApi.getItemImageUrl(
                    itemId = item.id,
                    imageType = ImageType.LOGO,
                    tag = tag,
                    maxHeight = LOGO_MAX_HEIGHT,
                )
            }
            AmbientSlide(
                backdropUrl = backdropUrl,
                title = item.name.orEmpty(),
                year = item.productionYear,
                logoUrl = logoUrl,
            )
        }
    }

    companion object {
        private const val SLIDE_LIMIT = 24
        private const val BACKDROP_MAX_WIDTH = 1280
        private const val LOGO_MAX_HEIGHT = 180
    }
}
