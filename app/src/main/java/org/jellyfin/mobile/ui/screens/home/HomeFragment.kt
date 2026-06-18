package org.jellyfin.mobile.ui.screens.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jellyfin.mobile.databinding.FragmentComposeBinding
import org.jellyfin.mobile.ui.screens.HEADER_HEIGHT
import org.jellyfin.mobile.ui.screens.PortalHeader
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
                Surface(color = MaterialTheme.colors.background) {
                    HomeContainer(
                        onItemClick = ::onItemClick,
                        onLibraryClick = ::onLibraryClick,
                    )
                }
            }
        }
    }

    private fun onItemClick(item: BaseItemDto) {
        // TODO(native-detail): open a native detail screen here. The earlier
        // WebView deep-link handoff looped (web re-routed back to home on the
        // Portal), so until the native detail screen exists a media-card tap is a
        // no-op — you still get the press-feedback animation, just no navigation.
        // Library navigation (onLibraryClick) works and is the way to browse.
    }

    private fun onLibraryClick(library: BaseItemDto) {
        // Dive into a library — native grid screen, pushed to the back stack.
        requireMainActivity().openLibrary(library)
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
    viewModel: HomeViewModel = viewModel(),
) {
    // Header is an OVERLAY, not a row: content scrolls BEHIND it. The header's
    // gradient apron is opaque at the very top (so the logo + Portal OSD buttons
    // stay legible) and fades to transparent at its lower edge, so rows dissolve
    // into it as they scroll up instead of hitting a hard gray bar. (True
    // backdrop-blur needs API 31; the Portal is API 28, so a fade is the move.)
    Box(modifier = Modifier.fillMaxSize()) {
        HomeScreen(
            onItemClick = onItemClick,
            onLibraryClick = onLibraryClick,
            viewModel = viewModel,
            topContentPadding = HEADER_HEIGHT,
        )
        PortalHeader(
            onLogoClick = { /* already home; no-op */ },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
