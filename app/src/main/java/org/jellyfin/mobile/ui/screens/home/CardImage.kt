package org.jellyfin.mobile.ui.screens.home

import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

/**
 * Which image to draw on a home card, and where to pull it from.
 *
 * The home rails (Continue Watching / Next Up / Latest) are landscape cards, so
 * we want wide art — a Thumb or Backdrop — not the portrait Primary poster
 * (that's what made the first build look wrong). This mirrors jellyfin-web's
 * fallback chain for these rows: the item's own Thumb, then a parent/series
 * Thumb, then a Backdrop, and only Primary as a last resort.
 */
private data class CardImageSource(
    val itemId: UUID,
    val imageType: ImageType,
    val tag: String?,
)

private fun BaseItemDto.landscapeImageSource(): CardImageSource {
    // 1. This item's own Thumb.
    imageTags?.get(ImageType.THUMB)?.let { tag ->
        return CardImageSource(id, ImageType.THUMB, tag)
    }
    // 2. A parent's / series' Thumb (episodes usually have no Thumb of their own).
    parentThumbItemId?.let { parentId ->
        return CardImageSource(parentId, ImageType.THUMB, parentThumbImageTag)
    }
    seriesThumbImageTag?.let { tag ->
        seriesId?.let { sid -> return CardImageSource(sid, ImageType.THUMB, tag) }
    }
    // 3. A Backdrop (this item's, then a parent's).
    backdropImageTags?.firstOrNull()?.let { tag ->
        return CardImageSource(id, ImageType.BACKDROP, tag)
    }
    parentBackdropItemId?.let { parentId ->
        parentBackdropImageTags?.firstOrNull()?.let { tag ->
            return CardImageSource(parentId, ImageType.BACKDROP, tag)
        }
    }
    // 4. Last resort: the portrait Primary poster.
    return CardImageSource(id, ImageType.PRIMARY, imageTags?.get(ImageType.PRIMARY))
}

/** Build the best landscape thumbnail URL for a home card. */
fun BaseItemDto.cardImageUrl(apiClient: ApiClient, maxWidth: Int): String {
    val source = landscapeImageSource()
    return apiClient.imageApi.getItemImageUrl(
        itemId = source.itemId,
        imageType = source.imageType,
        tag = source.tag,
        maxWidth = maxWidth,
    )
}
