package org.jellyfin.mobile.ui.screens.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.LaunchedEffect
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
import org.jellyfin.sdk.model.serializer.toUUIDOrNull

/**
 * Native library/category grid — the destination when a home library card is
 * tapped. Added to the back stack by MainActivity; the Portal OSD back button
 * pops it back to home (no on-screen back button or title — same header policy
 * as the home). Shares [PortalHeader]; the poster grid scrolls behind it.
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
        if (libraryId == null) {
            parentFragmentManager.popBackStack()
            return
        }

        composeView.setContent {
            AppTheme {
                Surface(color = MaterialTheme.colors.background) {
                    val vm: LibraryViewModel = viewModel()
                    LaunchedEffect(libraryId) { vm.load(libraryId) }
                    Box(modifier = Modifier.fillMaxSize()) {
                        LibraryScreen(
                            onItemClick = { /* TODO(native-detail): open native detail screen */ },
                            viewModel = vm,
                            topContentPadding = HEADER_HEIGHT,
                        )
                        // Logo returns to home: pop straight back to the home fragment.
                        PortalHeader(
                            onLogoClick = { requireMainActivity().popToHome() },
                            modifier = Modifier.align(Alignment.TopCenter),
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
