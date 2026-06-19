package org.jellyfin.mobile.downloads

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import org.jellyfin.mobile.app.ApiClientController
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto

class DownloadQueue(
    private val context: Context,
    private val apiClientController: ApiClientController,
    private val downloadDao: DownloadDao,
    private val downloadNotificationManager: DownloadNotificationManager,
    private val storageManager: StorageManager,
    private val appPreferences: AppPreferences,
    okHttpClient: OkHttpClient,
) {
    private val _downloader = FileDownloader(okHttpClient)
    private val _downloads = mutableListOf<DownloadEntity>()

    suspend fun prepare(): Boolean {
        val queuedDownloads = downloadDao.getQueuedDownloads()
        _downloads.clear()
        _downloads.addAll(queuedDownloads)
        return _downloads.any()
    }

    suspend fun process() {
        while (_downloads.any()) {
            val iterator = _downloads.iterator()
            while (iterator.hasNext()) {
                val download = iterator.next()
                process(download)
                iterator.remove()
            }

            // Refetch the queued downloads
            prepare()
        }
    }

    private suspend fun process(download: DownloadEntity) {
        // Mark as downloading
        downloadDao.update(download.copy(status = DownloadStatus.DOWNLOADING))

        try {
            val notificationProgressCallback = downloadNotificationManager.downloadFile(
                download.id,
                download.id.toString(),
            )

            val api = apiClientController.getApiClient(download.serverId, download.userId)

            val storageLocation = storageManager.getStorageLocation()
            val itemLocation = storageLocation.findFile(download.path) ?: storageLocation.createDirectory(download.path) ?: error("Unable to find or create folder ${download.path}")

            val filename = download(
                api = api,
                download = download,
                itemLocation = itemLocation,
                progressCallback = notificationProgressCallback,
            )

            notificationProgressCallback.onEnd()
            downloadDao.update(
                download.copy(status = DownloadStatus.DOWNLOADED, downloadFilename = filename),
            )
        } catch (_: CancellationException) {
            downloadDao.update(download.copy(status = DownloadStatus.QUEUED))
        } catch (error: Throwable) {
            downloadDao.update(download.copy(status = DownloadStatus.ERROR))
            throw error
        }
    }

    /**
     * Download a server-transcoded copy of [download] into [itemLocation].
     *
     * Rather than the original (often multi-GB) remux, we request an MP4
     * transcode at the user's chosen [DownloadQuality] so a feature film lands
     * around 1–2 GB. Returns the on-disk filename so it can be persisted on the
     * [DownloadEntity] (the original-path basename no longer matches).
     */
    private suspend fun download(
        api: ApiClient,
        download: DownloadEntity,
        itemLocation: DocumentFile,
        progressCallback: FileDownloader.ProgressCallback,
    ): String {
        val quality = appPreferences.downloadQuality
        // MPEG-TS, not MP4: Jellyfin's live transcode emits a *fragmented* MP4
        // with an empty moov (duration 0, no sidx), which ExoPlayer cannot seek —
        // scrubbing was dead. MPEG-TS supports constant-bitrate seeking, so the
        // downloaded copy is scrubbable. (See DownloadVideoExtractorsFactory.)
        val filename = "${download.itemId}.ts"

        // Use a mime of "" so DocumentFile.createFile doesn't append a second
        // extension to the name we already gave it. Recreate fresh each
        // run since transcodes aren't resumable.
        itemLocation.findFile(filename)?.delete()
        val fileLocation = itemLocation.createFile("", filename) ?: error("Unable to find or create file $filename")
        if (!fileLocation.canRead() || !fileLocation.canWrite()) error("Not allowed to read-write $fileLocation")

        val fileDescriptor = context.contentResolver.openFileDescriptor(fileLocation.uri, "rw") ?: error("Unable to open file descriptor for $fileLocation")

        _downloader.downloadAndSave(
            api,
            from = buildTranscodeUrl(api, download.itemId, quality).toUri(),
            to = fileDescriptor,
            progressCallback = progressCallback,
            resumable = false,
        )

        return filename
    }

    /**
     * Build a server-side transcode stream URL: H.264/AAC in an MP4 container,
     * capped at the quality preset's resolution and bitrate. `static=false`
     * forces the server to transcode rather than serve the original file.
     */
    private fun buildTranscodeUrl(api: ApiClient, itemId: java.util.UUID, quality: DownloadQuality): String {
        // A fresh play session per request so the server doesn't reuse a cached
        // transcode from a previous quality (Jellyfin keys transcode reuse on the
        // play session; without this, requesting 480p after 720p served the
        // cached 720p file and the maxHeight/bitRate caps were ignored).
        val sessionId = java.util.UUID.randomUUID().toString().replace("-", "")
        val params = linkedMapOf(
            "static" to "false",
            "container" to "ts",
            "videoCodec" to "h264",
            "audioCodec" to "aac",
            "maxHeight" to quality.maxHeight.toString(),
            "videoBitRate" to quality.videoBitRate.toString(),
            "audioBitRate" to quality.audioBitRate.toString(),
            "audioChannels" to "2",
            "playSessionId" to sessionId,
            "deviceId" to "pf-dl-$sessionId",
            "api_key" to (api.accessToken ?: ""),
        )
        val query = params.entries.joinToString("&") { (k, v) ->
            "$k=${java.net.URLEncoder.encode(v, "UTF-8")}"
        }
        // Log the transcode params (not the api_key) so download issues are
        // diagnosable without leaking the access token.
        timber.log.Timber.tag("PFDL").i(
            "transcode ${quality.label}: item=$itemId maxHeight=${quality.maxHeight} " +
                "vBitRate=${quality.videoBitRate} session=$sessionId",
        )
        return api.createUrl("/Videos/$itemId/stream.ts?$query")
    }
}
