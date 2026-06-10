package org.jellyfin.mobile.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.jellyfin.mobile.MainViewModel
import org.jellyfin.mobile.databinding.FragmentComposeBinding
import org.jellyfin.mobile.ui.screens.login.LoginScreen
import org.jellyfin.mobile.ui.utils.AppTheme
import org.jellyfin.mobile.utils.applyWindowInsetsAsMargins
import org.jellyfin.sdk.model.serializer.toUUID
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class LoginFragment : Fragment() {
    private val mainViewModel: MainViewModel by activityViewModel()
    private var _viewBinding: FragmentComposeBinding? = null
    private val viewBinding get() = _viewBinding!!
    private val composeView: ComposeView get() = viewBinding.composeView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _viewBinding = FragmentComposeBinding.inflate(inflater, container, false)
        return composeView.apply { applyWindowInsetsAsMargins() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.requestApplyInsets(composeView)

        val server = mainViewModel.serverState.value.server ?: run {
            // Defensive: if there's no server we shouldn't be here
            mainViewModel.resetServer()
            return
        }

        composeView.setContent {
            AppTheme {
                LoginScreen(
                    serverHostname = server.hostname,
                    onAuthenticated = { userId, accessToken, _serverId ->
                        lifecycleScope.launch {
                            mainViewModel.setupUser(
                                serverId = server.id,
                                userId = userId.toUUID(),
                                accessToken = accessToken,
                            )
                        }
                    },
                    onSwitchServer = {
                        mainViewModel.resetServer()
                    },
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _viewBinding = null
    }
}
