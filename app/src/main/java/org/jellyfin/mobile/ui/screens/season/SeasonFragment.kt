package org.jellyfin.mobile.ui.screens.season

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
import org.jellyfin.mobile.ui.screens.HEADER_HEIGHT
import org.jellyfin.mobile.ui.utils.AppTheme
import org.jellyfin.mobile.utils.extensions.requireMainActivity
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.serializer.toUUIDOrNull

/**
 * Native season detail — lists a season's episodes. Reached from a series detail
 * page's Seasons row; tapping an episode opens the (movie-style) episode detail.
 * Header is the static Activity-level overlay; OSD back returns.
 */
class SeasonFragment : Fragment() {
    private var _viewBinding: FragmentComposeBinding? = null
    private val viewBinding get() = _viewBinding!!
    private val composeView: ComposeView get() = viewBinding.composeView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _viewBinding = FragmentComposeBinding.inflate(inflater, container, false)
        return composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val seriesId = requireArguments().getString(ARG_SERIES_ID)?.toUUIDOrNull()
        val seasonId = requireArguments().getString(ARG_SEASON_ID)?.toUUIDOrNull()
        if (seriesId == null || seasonId == null) {
            parentFragmentManager.popBackStack()
            return
        }

        composeView.setContent {
            AppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val vm: SeasonViewModel = viewModel()
                    LaunchedEffect(seriesId, seasonId) { vm.load(seriesId, seasonId) }
                    SeasonScreen(
                        onEpisodeClick = { episode -> requireMainActivity().openDetail(episode) },
                        onSeasonClick = { season -> requireMainActivity().openSeason(season) },
                        viewModel = vm,
                        topContentPadding = HEADER_HEIGHT,
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _viewBinding = null
    }

    companion object {
        const val ARG_SERIES_ID = "series_id"
        const val ARG_SEASON_ID = "season_id"
        const val ARG_SEASON_NAME = "season_name"

        fun args(season: BaseItemDto): Bundle = Bundle().apply {
            putString(ARG_SERIES_ID, season.seriesId?.toString())
            putString(ARG_SEASON_ID, season.id.toString())
            putString(ARG_SEASON_NAME, season.name.orEmpty())
        }
    }
}
