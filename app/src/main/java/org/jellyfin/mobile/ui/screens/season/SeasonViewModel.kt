package org.jellyfin.mobile.ui.screens.season

import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.app.ApiClientController
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.downloads.DownloadManager
import org.jellyfin.mobile.downloads.DownloadQuality
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.File
import java.util.UUID

/**
 * Backs the season detail page — a full detail screen for one season: the season
 * item (image, overview), its episodes (getEpisodes), and the series' other
 * seasons (getSeasons) for the season selector. The injected [ApiClient] is authed.
 */
class SeasonViewModel : ViewModel(), KoinComponent {
    private val apiClient: ApiClient by inject()
    private val downloadManager: DownloadManager by inject()
    private val apiClientController: ApiClientController by inject()
    private val storageManager: StorageManager by inject()

    private val _state = MutableStateFlow<SeasonState>(SeasonState.Loading)
    val state: StateFlow<SeasonState> = _state.asStateFlow()

    /**
     * Estimate the season's transcoded size at [quality] and compare to free
     * space. Returns a plan the UI uses to confirm / warn / suggest a lower
     * quality before committing the download.
     */
    fun planSeasonDownload(quality: DownloadQuality = DownloadQuality.DEFAULT): SeasonDownloadPlan? {
        val content = _state.value as? SeasonState.Content ?: return null
        val episodes = content.episodes
        // Sum runtime (ticks → hours). Fall back to 30 min/episode when unknown.
        val totalHours = episodes.sumOf { ep ->
            val ticks = ep.runTimeTicks ?: (30L * 60 * 10_000_000)
            ticks / 10_000_000.0 / 3600.0
        }
        val estimatedBytes = (totalHours * quality.bytesPerHour).toLong()
        val freeBytes = freeSpaceBytes()
        // Keep a safety margin so we don't fill the disk to the brim.
        val fits = estimatedBytes < (freeBytes - SAFETY_MARGIN_BYTES)
        // If it doesn't fit, see whether the lowest preset would.
        val lowest = DownloadQuality.entries.minByOrNull { it.bytesPerHour }!!
        val suggestLower = if (!fits && quality != lowest) {
            val lowEstimate = (totalHours * lowest.bytesPerHour).toLong()
            if (lowEstimate < (freeBytes - SAFETY_MARGIN_BYTES)) lowest else null
        } else {
            null
        }
        return SeasonDownloadPlan(
            episodeCount = episodes.size,
            quality = quality,
            estimatedBytes = estimatedBytes,
            freeBytes = freeBytes,
            fits = fits,
            suggestLowerQuality = suggestLower,
        )
    }

    /** Enqueue every episode in the loaded season at [quality]. */
    fun downloadSeason(quality: DownloadQuality) {
        val content = _state.value as? SeasonState.Content ?: return
        val ids = content.episodes.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                val serverUser = apiClientController.loadSavedServerUser() ?: return@launch
                if (serverUser.user.accessToken == null) return@launch
                downloadManager.enqueueItems(
                    server = serverUser.server,
                    user = serverUser.user,
                    items = ids,
                    quality = quality,
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to enqueue season download")
            }
        }
    }

    private fun freeSpaceBytes(): Long = try {
        val path = storageManager.getStorageLocation().uri.path?.let { File(it) }
            ?: File(System.getProperty("java.io.tmpdir") ?: "/data")
        val stat = StatFs(if (path.exists()) path.path else "/data")
        stat.availableBytes
    } catch (e: Exception) {
        Timber.e(e, "Failed to read free space")
        Long.MAX_VALUE // don't block downloads if we can't read it
    }

    fun load(seriesId: UUID, seasonId: UUID) {
        _state.value = SeasonState.Loading
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    val seasonItem by apiClient.userLibraryApi.getItem(itemId = seasonId)
                    val episodesRes by apiClient.tvShowsApi.getEpisodes(seriesId = seriesId, seasonId = seasonId)
                    val seasonsRes by apiClient.tvShowsApi.getSeasons(seriesId = seriesId)
                    SeasonState.Content(
                        season = seasonItem,
                        episodes = episodesRes.items.orEmpty(),
                        seasons = seasonsRes.items.orEmpty(),
                    )
                }
                _state.value = content
            } catch (e: Exception) {
                Timber.e(e, "Failed to load season")
                _state.value = SeasonState.Error(e.message ?: "Couldn't load this season")
            }
        }
    }
}

sealed interface SeasonState {
    data object Loading : SeasonState
    data class Content(
        val season: BaseItemDto,
        val episodes: List<BaseItemDto>,
        val seasons: List<BaseItemDto>,
    ) : SeasonState
    data class Error(val message: String) : SeasonState
}

/**
 * The result of sizing a season download against free space — the UI shows this
 * in a confirm dialog: estimated size, free space, whether it fits, and (if not)
 * a lower-quality preset that would.
 */
data class SeasonDownloadPlan(
    val episodeCount: Int,
    val quality: DownloadQuality,
    val estimatedBytes: Long,
    val freeBytes: Long,
    val fits: Boolean,
    val suggestLowerQuality: DownloadQuality?,
)

private const val SAFETY_MARGIN_BYTES = 500L * 1024 * 1024 // keep 500 MB headroom
