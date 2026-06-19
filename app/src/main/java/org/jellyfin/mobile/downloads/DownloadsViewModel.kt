package org.jellyfin.mobile.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.utils.lengthRecursive
import org.jellyfin.sdk.model.api.MediaType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DownloadsViewModel : ViewModel(), KoinComponent {

    private val downloadDao: DownloadDao by inject()
    private val downloadManager: DownloadManager by inject()
    private val activityEventHandler: ActivityEventHandler by inject()
    private val storageManager: StorageManager by inject()

    val downloads: StateFlow<List<DownloadEntity>> = downloadDao
        .getAllDownloads()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    /** Device storage snapshot for the Downloads screen's storage bar. */
    val storage: StateFlow<StorageInfo?> = downloadDao
        .getAllDownloads()
        .map { computeStorage(it) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private fun computeStorage(downloads: List<DownloadEntity>): StorageInfo {
        val location = storageManager.getStorageLocation()
        val path = location.uri.path?.let { java.io.File(it) }?.takeIf { it.exists() }
            ?: java.io.File("/data")
        val stat = android.os.StatFs(path.path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        // Bytes used by portalfin downloads specifically (sum of each item folder).
        val used = downloads.sumOf { d ->
            location.findFile(d.path)?.lengthRecursive() ?: 0L
        }
        return StorageInfo(totalBytes = total, freeBytes = free, downloadsBytes = used)
    }

    fun openDownload(download: DownloadEntity) {
        when (download.item.mediaType) {
            MediaType.VIDEO,
            MediaType.AUDIO,
            -> {
                val playOptions = PlayOptions(
                    ids = listOf(download.itemId),
                    mediaSourceId = download.itemId.toString(),
                    startIndex = 0,
                    startPosition = null,
                    audioStreamIndex = null,
                    subtitleStreamIndex = null,
                    playFromDownloads = true,
                )
                activityEventHandler.emit(ActivityEvent.LaunchNativePlayer(playOptions))
            }

            MediaType.PHOTO,
            MediaType.BOOK,
            MediaType.UNKNOWN,
            -> {
                viewModelScope.launch {
                    val fileUri = withContext(Dispatchers.IO) {
                        val storageLocation = storageManager.getStorageLocation()
                        val itemLocation = storageLocation.findFile(download.path)
                        if (itemLocation != null && itemLocation.isDirectory) {
                            val filename = download.downloadFilename
                                ?: download.item.path?.replace(Regex("^.*[\\\\/]"), "")
                            if (filename != null) itemLocation.findFile(filename)?.uri else null
                        } else {
                            null
                        }
                    }
                    fileUri?.let { activityEventHandler.emit(ActivityEvent.OpenUrl(it.toString(), true)) }
                }
            }
        }
    }

    fun download(download: DownloadEntity) {
        viewModelScope.launch {
            downloadManager.resume(download)
        }
    }

    fun removeDownload(download: DownloadEntity, deleteFiles: Boolean) {
        viewModelScope.launch {
            downloadManager.delete(download.id, deleteFiles)
        }
    }
}

/** Device storage snapshot for the Downloads screen's storage bar. */
data class StorageInfo(
    val totalBytes: Long,
    val freeBytes: Long,
    /** Bytes used by portalfin downloads specifically. */
    val downloadsBytes: Long,
) {
    /** Bytes used by everything else on the volume. */
    val otherUsedBytes: Long get() = (totalBytes - freeBytes - downloadsBytes).coerceAtLeast(0L)
}
