package org.jellyfin.mobile.ui.screens.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.databinding.FragmentComposeBinding
import org.jellyfin.mobile.downloads.DownloadStatus
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.ui.screens.HEADER_HEIGHT
import org.jellyfin.mobile.utils.extensions.requireMainActivity
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.android.ext.android.inject

/**
 * Native detail screen — opened when a poster/home card is tapped. Added to the
 * back stack by MainActivity; the Portal OSD back button returns to wherever you
 * came from. The header is the static Activity-level overlay (MainActivity), not
 * rendered here. Play launches the native ExoPlayer via
 * [ActivityEvent.LaunchNativePlayer] (no WebView).
 */
class DetailFragment : Fragment() {
    private val activityEventHandler: ActivityEventHandler by inject()
    private val downloadDao: DownloadDao by inject()
    private var _viewBinding: FragmentComposeBinding? = null
    private val viewBinding get() = _viewBinding!!
    private val composeView: ComposeView get() = viewBinding.composeView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _viewBinding = FragmentComposeBinding.inflate(inflater, container, false)
        return composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val itemId = requireArguments().getString(ARG_ITEM_ID)?.toUUIDOrNull()
        if (itemId == null) {
            parentFragmentManager.popBackStack()
            return
        }

        composeView.setContent {
            org.jellyfin.mobile.ui.utils.AppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val vm: DetailViewModel = viewModel()
                    LaunchedEffect(itemId) { vm.load(itemId) }
                    // The portalfin header is the STATIC Activity-level overlay (see
                    // MainActivity) — do NOT render one here too (that double-stacked
                    // the apron on this screen). Just reserve its height.
                    DetailScreen(
                        onPlay = ::play,
                        onItemClick = ::openItem,
                        onOpenDownloads = { requireMainActivity().openDownloads() },
                        viewModel = vm,
                        topContentPadding = HEADER_HEIGHT,
                    )
                }
            }
        }
    }

    /** A season opens its episode list; anything else opens a detail page. */
    private fun openItem(item: BaseItemDto) {
        if (item.type == org.jellyfin.sdk.model.api.BaseItemKind.SEASON) {
            requireMainActivity().openSeason(item)
        } else {
            requireMainActivity().openDetail(item)
        }
    }

    private fun play(item: BaseItemDto, startTicks: Long, subtitleIndex: Int?) {
        // If this item is downloaded, play the local copy (offline + instant)
        // instead of streaming. Checked off the main thread, then launched.
        lifecycleScope.launch {
            val isDownloaded = withContext(Dispatchers.IO) {
                downloadDao.getDownloadByItemId(item.id)?.status == DownloadStatus.DOWNLOADED
            }
            activityEventHandler.emit(
                ActivityEvent.LaunchNativePlayer(
                    PlayOptions(
                        ids = listOf(item.id),
                        // Download playback resolves the source by itemId; for it to
                        // take the download branch, mediaSourceId must be non-null too.
                        mediaSourceId = if (isDownloaded) item.id.toString() else null,
                        startIndex = 0,
                        // Resume from the saved position; "Start Over" passes 0.
                        startPosition = startTicks.takeIf { it > 0 }?.ticks,
                        audioStreamIndex = null,
                        subtitleStreamIndex = subtitleIndex,
                        playFromDownloads = isDownloaded,
                    ),
                ),
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _viewBinding = null
    }

    companion object {
        const val ARG_ITEM_ID = "item_id"

        fun args(item: BaseItemDto): Bundle = Bundle().apply {
            putString(ARG_ITEM_ID, item.id.toString())
        }
    }
}
