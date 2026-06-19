package org.jellyfin.mobile.downloads

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.app.ApiClientController
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.UUID

class DownloadBroadcastReceiver : BroadcastReceiver(), KoinComponent {
    companion object {
        private const val ACTION_DOWNLOAD_CANCEL = "download_cancel"
        private const val EXTRA_DOWNLOAD_ID = "download_id"

        // --- DEBUG-ONLY headless test harness (see onReceive) ---------------
        // These let ADB drive the real download/transcode/delete flow without
        // tapping Compose UI (synthetic taps don't fire Compose clickables).
        // Guarded by BuildConfig.DEBUG so they never run in a release build.
        // All output is logged under the "PFDL" Timber tag.
        //   adb shell am broadcast -a org.jellyfin.mobile.DL_ENQUEUE \
        //       -n <pkg>/org.jellyfin.mobile.downloads.DownloadBroadcastReceiver \
        //       --es name "Back to the Future" --ei quality 0
        //   adb shell am broadcast -a org.jellyfin.mobile.DL_LIST  -n <pkg>/...Receiver
        //   adb shell am broadcast -a org.jellyfin.mobile.DL_DELETE -n <pkg>/...Receiver --el id 3
        private const val ACTION_DL_ENQUEUE = "org.jellyfin.mobile.DL_ENQUEUE"
        private const val ACTION_DL_LIST = "org.jellyfin.mobile.DL_LIST"
        private const val ACTION_DL_DELETE = "org.jellyfin.mobile.DL_DELETE"
        private const val LOG = "PFDL"

        fun cancelDownloadIntent(context: Context, downloadId: Long) = Intent(
            context,
            DownloadBroadcastReceiver::class.java,
        ).apply {
            action = ACTION_DOWNLOAD_CANCEL
            putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        }
    }

    private val downloadManager by inject<DownloadManager>()
    private val downloadDao by inject<DownloadDao>()
    private val apiClientController by inject<ApiClientController>()
    private val apiClient by inject<ApiClient>()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DOWNLOAD_CANCEL -> handleCancel(intent)
            ACTION_DL_ENQUEUE -> if (BuildConfig.DEBUG) debugEnqueue(intent)
            ACTION_DL_LIST -> if (BuildConfig.DEBUG) debugList()
            ACTION_DL_DELETE -> if (BuildConfig.DEBUG) debugDelete(intent)
        }
    }

    private fun handleCancel(intent: Intent) {
        val id = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
        if (id == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob()).launch {
            try {
                downloadManager.cancel(id)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Resolve an item (by --es name search, or --es itemId UUID), then enqueue
     * it for transcoded download at --ei quality (0 = 1080p, 1 = 720p).
     */
    private fun debugEnqueue(intent: Intent) {
        val name = intent.getStringExtra("name")
        val itemIdArg = intent.getStringExtra("itemId")
        val qualityInt = intent.getIntExtra("quality", DownloadQuality.DEFAULT.intValue)
        val quality = DownloadQuality.fromInt(qualityInt) ?: DownloadQuality.DEFAULT

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob()).launch {
            try {
                val serverUser = apiClientController.loadSavedServerUser()
                if (serverUser?.user?.accessToken == null) {
                    Timber.tag(LOG).e("ENQUEUE: no signed-in server/user — sign in first")
                    return@launch
                }

                val itemId: UUID? = when {
                    itemIdArg != null -> runCatching { UUID.fromString(itemIdArg) }.getOrNull()
                    name != null -> resolveItemIdByName(name)
                    else -> null
                }
                if (itemId == null) {
                    Timber.tag(LOG).e("ENQUEUE: could not resolve item (name=$name itemId=$itemIdArg)")
                    return@launch
                }

                Timber.tag(LOG).i("ENQUEUE: item=$itemId quality=$quality (${quality.maxHeight}p) — starting")
                downloadManager.enqueueItems(
                    server = serverUser.server,
                    user = serverUser.user,
                    items = listOf(itemId),
                    quality = quality,
                )
                Timber.tag(LOG).i("ENQUEUE: queued; DownloadWorker started. Poll with DL_LIST.")
            } catch (e: Throwable) {
                Timber.tag(LOG).e(e, "ENQUEUE failed")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun resolveItemIdByName(name: String): UUID? {
        val response by apiClient.itemsApi.getItems(
            searchTerm = name,
            recursive = true,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE, BaseItemKind.VIDEO),
            limit = 1,
        )
        val hit = response.items.orEmpty().firstOrNull()
        Timber.tag(LOG).i("resolve \"$name\" -> ${hit?.name} (${hit?.id})")
        return hit?.id
    }

    /** Log every download row: id, status, on-disk filename, item name. */
    private fun debugList() {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob()).launch {
            try {
                val rows = downloadDao.getAllDownloads().first()
                Timber.tag(LOG).i("LIST: ${rows.size} download(s) total")
                rows.forEach { d ->
                    Timber.tag(LOG).i("  [${d.id}] ${d.status} file=${d.downloadFilename} item=${d.item.name} path=${d.path}")
                }
            } catch (e: Throwable) {
                Timber.tag(LOG).e(e, "LIST failed")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun debugDelete(intent: Intent) {
        val id = intent.getLongExtra("id", -1L)
        if (id == -1L) {
            Timber.tag(LOG).e("DELETE: pass --el id <downloadId>")
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob()).launch {
            try {
                downloadManager.delete(id, deleteFiles = true)
                Timber.tag(LOG).i("DELETE: removed download $id (files deleted)")
            } catch (e: Throwable) {
                Timber.tag(LOG).e(e, "DELETE failed")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
