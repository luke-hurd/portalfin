package org.jellyfin.mobile.downloads

import androidx.compose.runtime.Composable
import org.jellyfin.mobile.ui.ComposeFragment
import org.jellyfin.mobile.ui.screens.HEADER_HEIGHT
import org.jellyfin.mobile.ui.screens.downloads.DownloadsScreen

class DownloadsFragment : ComposeFragment() {
    @Composable
    override fun Content() {
        // Shares the static portalfin header (shown by MainActivity); reserve its
        // height so the content scrolls behind it, matching the other native views.
        DownloadsScreen(
            onBackPressed = {
                isAdded && !parentFragmentManager.isStateSaved && parentFragmentManager.popBackStackImmediate()
            },
            topContentPadding = HEADER_HEIGHT,
        )
    }
}
