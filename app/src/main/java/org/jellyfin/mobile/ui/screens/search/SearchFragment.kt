package org.jellyfin.mobile.ui.screens.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jellyfin.mobile.databinding.FragmentComposeBinding
import org.jellyfin.mobile.ui.screens.HEADER_HEIGHT
import org.jellyfin.mobile.ui.utils.AppTheme
import org.jellyfin.mobile.utils.extensions.requireMainActivity

/**
 * Native search screen — REST API search (no WebView). Reached from the home
 * Search card; added to the back stack so the Portal OSD back button pops it
 * back to home. Shares [org.jellyfin.mobile.ui.screens.PortalHeader]; results
 * scroll behind it.
 */
class SearchFragment : Fragment() {
    private var _viewBinding: FragmentComposeBinding? = null
    private val composeView: ComposeView get() = _viewBinding!!.composeView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _viewBinding = FragmentComposeBinding.inflate(inflater, container, false)
        return composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        composeView.setContent {
            AppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SearchScreen(
                        onItemClick = { item -> requireMainActivity().openDetail(item) },
                        viewModel = viewModel(),
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
}
