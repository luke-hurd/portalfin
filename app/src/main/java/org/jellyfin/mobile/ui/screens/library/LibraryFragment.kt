package org.jellyfin.mobile.ui.screens.library

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jellyfin.mobile.databinding.FragmentComposeBinding
import org.jellyfin.mobile.ui.utils.AppTheme
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.serializer.toUUIDOrNull

/**
 * Native library grid — the destination when a home category chip is tapped.
 * Added to the back stack by MainActivity, so the system back returns to the
 * native home. Top inset handled inside Compose (same approach as HomeFragment).
 */
class LibraryFragment : Fragment() {
    private var _viewBinding: FragmentComposeBinding? = null
    private val viewBinding get() = _viewBinding!!
    private val composeView: ComposeView get() = viewBinding.composeView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _viewBinding = FragmentComposeBinding.inflate(inflater, container, false)
        return composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val libraryId = requireArguments().getString(ARG_LIBRARY_ID)?.toUUIDOrNull()
        val libraryName = requireArguments().getString(ARG_LIBRARY_NAME).orEmpty()
        if (libraryId == null) {
            parentFragmentManager.popBackStack()
            return
        }

        composeView.setContent {
            AppTheme {
                Surface(color = MaterialTheme.colors.background) {
                    val vm: LibraryViewModel = viewModel()
                    androidx.compose.runtime.LaunchedEffect(libraryId) { vm.load(libraryId) }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = portalTopReserve()),
                    ) {
                        LibraryScreen(
                            title = libraryName,
                            onItemClick = { /* TODO(native-detail): open native detail screen */ },
                            viewModel = vm,
                        )
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _viewBinding = null
    }

    companion object {
        const val ARG_LIBRARY_ID = "library_id"
        const val ARG_LIBRARY_NAME = "library_name"

        fun args(library: BaseItemDto): Bundle = Bundle().apply {
            putString(ARG_LIBRARY_ID, library.id.toString())
            putString(ARG_LIBRARY_NAME, library.name.orEmpty())
        }
    }
}

@Composable
private fun portalTopReserve(): Dp {
    val isPortal = Build.DEVICE == "aloha"
    val systemTop = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    return if (isPortal) maxOf(systemTop, 64.dp) else systemTop
}
