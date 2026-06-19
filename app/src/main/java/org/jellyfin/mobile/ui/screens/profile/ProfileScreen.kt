package org.jellyfin.mobile.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jellyfin.mobile.ui.utils.PortalColors
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.ImageType
import org.koin.compose.koinInject

private val EDGE_PADDING = 28.dp
private val SECTION_SPACING = 20.dp
private val AVATAR = 88.dp
private const val AVATAR_PX = 220

/**
 * Native profile / settings screen — no WebView. Shows the signed-in user, the
 * native-home toggle, and account actions, all on the Portal design system.
 */
@Composable
fun ProfileScreen(
    serverHostname: String,
    nativeHomeEnabled: Boolean,
    onNativeHomeChange: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onSwitchServer: () -> Unit,
    viewModel: ProfileViewModel,
    topContentPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = topContentPadding + EDGE_PADDING, bottom = EDGE_PADDING)
            .padding(horizontal = EDGE_PADDING),
        verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
    ) {
        ProfileHeader(state)

        SectionCard(title = "Interface") {
            ToggleRow(
                label = "Native home (beta)",
                checked = nativeHomeEnabled,
                onCheckedChange = onNativeHomeChange,
            )
        }

        SectionCard(title = "Account") {
            // Switch Server is the primary action (blue); Sign Out is destructive (red).
            ActionButton(text = "Switch Server", onClick = onSwitchServer)
            ActionButton(text = "Sign Out", onClick = onSignOut, containerColor = PortalColors.Error)
        }
    }
}

@Composable
private fun ProfileHeader(state: ProfileState) {
    val apiClient: ApiClient = koinInject()
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier.size(AVATAR).clip(CircleShape).background(PortalColors.SurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val userId = state.userId
            val tag = state.avatarTag
            if (userId != null && tag != null) {
                val req = remember(apiClient, userId, tag) {
                    val url = apiClient.imageApi.getItemImageUrl(
                        itemId = userId,
                        imageType = ImageType.PRIMARY,
                        tag = tag,
                        maxWidth = AVATAR_PX,
                    )
                    ImageRequest.Builder(context).data(url).crossfade(true).build()
                }
                AsyncImage(
                    model = req,
                    contentDescription = state.userName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(AVATAR),
                )
            } else {
                Icon(Icons.Filled.Person, contentDescription = null, tint = PortalColors.OnSurface, modifier = Modifier.size(44.dp))
            }
        }
        Column {
            Text(
                text = state.userName ?: "Signed in",
                style = MaterialTheme.typography.headlineSmall,
                color = PortalColors.OnBackground,
            )
            Text(
                text = state.serverHostname,
                style = MaterialTheme.typography.bodyMedium,
                color = PortalColors.OnSurface,
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = PortalColors.OnBackground)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PortalColors.Surface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = PortalColors.OnBackground)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color = PortalColors.MetaBlue,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = PortalColors.OnBackground,
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

