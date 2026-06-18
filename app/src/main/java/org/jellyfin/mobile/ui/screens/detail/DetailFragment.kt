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
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jellyfin.mobile.databinding.FragmentComposeBinding
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.ui.screens.HEADER_HEIGHT
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
                        viewModel = vm,
                        topContentPadding = HEADER_HEIGHT,
                    )
                }
            }
        }
    }

    private fun play(item: BaseItemDto, startTicks: Long) {
        activityEventHandler.emit(
            ActivityEvent.LaunchNativePlayer(
                PlayOptions(
                    ids = listOf(item.id),
                    mediaSourceId = null,
                    startIndex = 0,
                    // Resume from the saved position; "Start Over" passes 0.
                    startPosition = startTicks.takeIf { it > 0 }?.ticks,
                    audioStreamIndex = null,
                    subtitleStreamIndex = null,
                    playFromDownloads = false,
                ),
            ),
        )
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
