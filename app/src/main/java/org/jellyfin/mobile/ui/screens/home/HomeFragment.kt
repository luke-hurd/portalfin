package org.jellyfin.mobile.ui.screens.home

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.jellyfin.mobile.databinding.FragmentComposeBinding
import org.jellyfin.mobile.ui.utils.AppTheme
import org.jellyfin.mobile.utils.extensions.requireMainActivity
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Native Portal home screen (roadmap: replace jellyfin-web's React home with
 * native Compose tiles). The Portal top-inset reserve (the system back/home
 * band) is applied inside Compose via [portalTopReserve]; the View-level
 * applyWindowInsetsAsMargins() listener doesn't reliably fire for a ComposeView.
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
        // TODO(native-detail): replace this WebView handoff with a native detail
        // screen. For now, tapping a card deep-links jellyfin-web's detail route
        // so playback still works end-to-end while the home grid is built out.
        requireMainActivity().openWebViewAt("/web/#/details?id=${item.id}")
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Reserve the Portal's top system band (back/home buttons live here).
            // The View-level applyWindowInsetsAsMargins() listener doesn't reliably
            // fire for a ComposeView, so we apply the reserve inside Compose. Like
            // the View helper, floor it at 64dp on Portal because the OS can report
            // insets.top == 0 — that zero is exactly the recurring "lost gap" bug.
            // CLAUDE.md rule #3 / memory: portal-top-inset-detection.
            .padding(top = portalTopReserve()),
    ) {
        HomeHeader()
        Box(modifier = Modifier.fillMaxSize()) {
            HomeScreen(
                onItemClick = onItemClick,
                onLibraryClick = onLibraryClick,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun portalTopReserve(): Dp {
    val isPortal = Build.DEVICE == "aloha"
    val systemTop = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    return if (isPortal) maxOf(systemTop, PORTAL_TOP_RESERVE_DP) else systemTop
}

private val PORTAL_TOP_RESERVE_DP = 64.dp

private val WORDMARK_HEIGHT = 36.dp
private const val WORDMARK_ASPECT = 1080f / 358f

@Composable
private fun HomeHeader() {
    // The portalfin wordmark, served as a raw asset (file:///android_asset/...).
    // Left-padded so it lines up under the Portal back/home pills above it,
    // matching the WebView header treatment.
    Box(
        modifier = Modifier
            .height(56.dp)
            .padding(start = 24.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // wordmark.png is 1080x358 (~3.017:1). Size by height and match that true
        // aspect ratio so it's a clean downscale, not a blurry letterboxed fit.
        AsyncImage(
            model = "file:///android_asset/native/wordmark.png",
            contentDescription = "portalfin",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .height(WORDMARK_HEIGHT)
                .aspectRatio(WORDMARK_ASPECT),
        )
    }
}
