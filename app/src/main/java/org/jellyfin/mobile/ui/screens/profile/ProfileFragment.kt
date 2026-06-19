package org.jellyfin.mobile.ui.screens.profile

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
import kotlinx.coroutines.launch
import org.jellyfin.mobile.MainViewModel
import org.jellyfin.mobile.databinding.FragmentComposeBinding
import org.jellyfin.mobile.ui.screens.HEADER_HEIGHT
import org.jellyfin.mobile.ui.utils.AppTheme
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * Native profile / settings screen — replaces the old jellyfin-web settings.
 * Opened from the home Settings card; pushed on the back stack (OSD back returns).
 */
class ProfileFragment : Fragment() {
    private val mainViewModel: MainViewModel by activityViewModel()
    private var _viewBinding: FragmentComposeBinding? = null
    private val viewBinding get() = _viewBinding!!
    private val composeView: ComposeView get() = viewBinding.composeView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _viewBinding = FragmentComposeBinding.inflate(inflater, container, false)
        return composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val hostname = mainViewModel.serverState.value.server?.hostname.orEmpty()

        composeView.setContent {
            AppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val vm: ProfileViewModel = viewModel()
                    LaunchedEffect(hostname) { vm.load(hostname) }

                    ProfileScreen(
                        serverHostname = hostname,
                        onSignOut = { lifecycleScope.launch { mainViewModel.signOut() } },
                        onSwitchServer = { mainViewModel.resetServer() },
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
}
