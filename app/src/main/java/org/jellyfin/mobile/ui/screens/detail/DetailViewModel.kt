package org.jellyfin.mobile.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.UUID

/**
 * Backs the native detail screen — the destination when a poster/card is tapped.
 * Fetches the full item (overview, genres, runtime, ratings, …) via getItem; the
 * injected [ApiClient] is already authenticated.
 */
class DetailViewModel : ViewModel(), KoinComponent {
    private val apiClient: ApiClient by inject()

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state.asStateFlow()

    fun load(itemId: UUID) {
        _state.value = DetailState.Loading
        viewModelScope.launch {
            try {
                val item = withContext(Dispatchers.IO) {
                    val result by apiClient.userLibraryApi.getItem(itemId = itemId)
                    result
                }
                _state.value = DetailState.Content(item)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load detail")
                _state.value = DetailState.Error(e.message ?: "Couldn't load this title")
            }
        }
    }
}

sealed interface DetailState {
    data object Loading : DetailState
    data class Content(val item: BaseItemDto) : DetailState
    data class Error(val message: String) : DetailState
}
