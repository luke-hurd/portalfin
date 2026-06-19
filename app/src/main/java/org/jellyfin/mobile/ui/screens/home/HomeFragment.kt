package org.jellyfin.mobile.ui.screens.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jellyfin.mobile.databinding.FragmentComposeBinding
import org.jellyfin.mobile.ui.screens.HEADER_HEIGHT
import org.jellyfin.mobile.ui.utils.AppTheme
import org.jellyfin.mobile.utils.extensions.requireMainActivity
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Native Portal home screen (roadmap: replace jellyfin-web's React home with
 * native Compose tiles).
 *
 * Header policy (CHANGED 2026-06-18, deliberate reversal of the old top-inset
 * rule): we no longer reserve the 64dp Portal top band. The Portal's back/home
 * OSD buttons stay visible in the top-LEFT; our header sits at the very top and
 * the centered wordmark shares that band with them (they don't collide because
 * the logo is centered, away from the left buttons). This reclaims vertical
 * space. See memory: portal-top-inset-detection (rule updated).
 *
 * Flag-gated by AppPreferences.useNativeHome (Settings → Interface). When off,
 * MainActivity shows the WebView home as before.
 */
class HomeFragment : Fragment() {
    private var _viewBinding: FragmentComposeBinding? = null
    private val viewBinding get() = _viewBinding!!
    private val composeView: ComposeView get() = viewBinding.composeView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _viewBinding = FragmentComposeBinding.inflate(inflater, container, false)
        return composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        composeView.setContent {
            AppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    HomeContainer(
                        onItemClick = ::onItemClick,
                        onLibraryClick = ::onLibraryClick,
                        onSettingsClick = ::onSettingsClick,
                        onSearchClick = ::onSearchClick,
                    )
                }
            }
        }
    }

    private fun onItemClick(item: BaseItemDto) {
        // Open the native detail screen (animated, pushed to the back stack).
        requireMainActivity().openDetail(item)
    }

    private fun onLibraryClick(library: BaseItemDto) {
        // Dive into a library — native grid screen, pushed to the back stack.
        requireMainActivity().openLibrary(library)
    }

    private fun onSettingsClick() {
        requireMainActivity().openSettings()
    }

    private fun onSearchClick() {
        requireMainActivity().openSearch()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _viewBinding = null
    }
}

@Composable
private fun HomeContainer(
    onItemClick: (BaseItemDto) -> Unit,
    onLibraryClick: (BaseItemDto) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    // The portalfin header is a STATIC Activity-level overlay (see MainActivity)
    // so it doesn't animate with fragment transitions. Here we just reserve the
    // space it covers via topContentPadding; content scrolls behind it.
    HomeScreen(
        onItemClick = onItemClick,
        onLibraryClick = onLibraryClick,
        onSettingsClick = onSettingsClick,
        onSearchClick = onSearchClick,
        viewModel = viewModel,
        topContentPadding = HEADER_HEIGHT,
    )
}
