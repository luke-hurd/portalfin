package org.jellyfin.mobile.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.UUID

/**
 * Backs the native profile / settings screen. Loads the signed-in user (name +
 * avatar tag) and the server URL. The injected [ApiClient] is authenticated.
 */
class ProfileViewModel : ViewModel(), KoinComponent {
    private val apiClient: ApiClient by inject()

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    fun load(serverHostname: String) {
        _state.value = ProfileState(serverHostname = serverHostname, loading = true)
        viewModelScope.launch {
            try {
                val user = withContext(Dispatchers.IO) {
                    val result by apiClient.userApi.getCurrentUser()
                    result
                }
                _state.value = ProfileState(
                    serverHostname = serverHostname,
                    userName = user.name,
                    userId = user.id,
                    avatarTag = user.primaryImageTag,
                    loading = false,
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load current user")
                _state.value = _state.value.copy(loading = false)
            }
        }
    }
}

data class ProfileState(
    val serverHostname: String = "",
    val userName: String? = null,
    val userId: UUID? = null,
    val avatarTag: String? = null,
    val loading: Boolean = true,
)
