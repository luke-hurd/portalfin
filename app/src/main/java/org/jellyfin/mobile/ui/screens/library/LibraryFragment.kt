package org.jellyfin.mobile.ui.screens.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jellyfin.mobile.databinding.FragmentComposeBinding
import org.jellyfin.mobile.ui.screens.HEADER_HEIGHT
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
        val libraryName = requireArguments().getString(ARG_LIBRARY_NAME).orEmpty()
        if (libraryId == null) {
            parentFragmentManager.popBackStack()
            return
        }

        composeView.setContent {
            AppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    // Header is a STATIC Activity-level overlay (see MainActivity);
                    // we only reserve its space so posters scroll behind it. The
                    // screen owns the filter state and drives the right ViewModel.
                    LibraryScreen(
                        title = libraryName,
                        libraryId = libraryId,
                        onItemClick = { item -> requireMainActivity().openDetail(item) },
                        viewModel = viewModel(),
                        groupViewModel = viewModel(),
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

    companion object {
        const val ARG_LIBRARY_ID = "library_id"
        const val ARG_LIBRARY_NAME = "library_name"

        fun args(library: BaseItemDto): Bundle = Bundle().apply {
            putString(ARG_LIBRARY_ID, library.id.toString())
            putString(ARG_LIBRARY_NAME, library.name.orEmpty())
        }
    }
}
