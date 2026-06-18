package org.jellyfin.mobile.ui.screens.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
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
        HomeHeader(
            onLogoClick = { /* already home; no-op */ },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

// Centered wordmark, tappable (returns to home). +20% then +5% from the original
// 36dp ≈ 45dp. wordmark.png is 1080x358 (~3.017:1) — size by height and match
// that true aspect ratio so it's a clean downscale, not a blurry fit.
private val WORDMARK_HEIGHT = 45.dp
private const val WORDMARK_ASPECT = 1080f / 358f
private val HEADER_HEIGHT = 64.dp

@Composable
private fun HomeHeader(onLogoClick: () -> Unit, modifier: Modifier = Modifier) {
    // Darken the top toward black so the logo + Portal OSD buttons sit on a
    // slightly darker apron than the page background.
    val top = lerp(MaterialTheme.colors.background, Color.Black, 0.35f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            // Translucent apron: near-solid at the top, holding opacity through the
            // upper half, then a tall fade to transparent so scrolled content
            // dissolves under it gradually instead of hitting a hard edge.
            .background(
                Brush.verticalGradient(
                    0f to top.copy(alpha = 0.98f),
                    0.5f to top.copy(alpha = 0.92f),
                    0.8f to top.copy(alpha = 0.6f),
                    1f to top.copy(alpha = 0f),
                ),
            ),
        // Anchor the logo in the darker, opaque upper zone (not the faded bottom).
        contentAlignment = Alignment.TopCenter,
    ) {
        AsyncImage(
            model = "file:///android_asset/native/wordmark.png",
            contentDescription = "portalfin — home",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(top = 10.dp)
                .height(WORDMARK_HEIGHT)
                .aspectRatio(WORDMARK_ASPECT)
                .clickable(onClick = onLogoClick),
        )
    }
}
