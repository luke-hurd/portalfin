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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.ui.screens.pressable
import org.jellyfin.mobile.ui.utils.PortalColors
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.ImageType
import org.koin.compose.koinInject

private val EDGE_PADDING = 28.dp
private val SECTION_SPACING = 20.dp
private val AVATAR = 88.dp
private const val AVATAR_PX = 220
private const val REPO_URL = "https://github.com/luke-hurd/portalfin"

/**
 * Native profile / settings screen — no WebView. Shows the signed-in user,
 * account actions, and an About section, all on the Portal design system.
 */
@Composable
fun ProfileScreen(
    serverHostname: String,
    onSignOut: () -> Unit,
    onSwitchServer: () -> Unit,
    viewModel: ProfileViewModel,
    topContentPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = topContentPadding + EDGE_PADDING, bottom = EDGE_PADDING)
            .padding(horizontal = EDGE_PADDING),
        verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
    ) {
        ProfileHeader(state)

        SectionCard(title = "Account") {
            // Switch Server is the primary action (blue); Sign Out is destructive (red).
            ActionButton(text = "Switch Server", onClick = onSwitchServer)
            ActionButton(text = "Sign Out", onClick = onSignOut, containerColor = PortalColors.Error)
        }

        ImmortalNavSection()

        AboutSection(onOpenRepo = { uriHandler.openUri(REPO_URL) })
    }
}

/**
 * "Navigation" setting: choose how the on-screen back/home nav behaves. Auto
 * detects the Immortal launcher (which has no OEM OSD pills); On/Off force it.
 * Stored in [AppPreferences.immortalNavMode]; MainActivity re-reads it on resume.
 */
@Composable
private fun ImmortalNavSection() {
    val appPreferences: AppPreferences = koinInject()
    var mode by remember { mutableStateOf(appPreferences.immortalNavMode) }

    SectionCard(title = "Navigation") {
        Text(
            text = "On-screen back & home buttons. Auto turns them on when the " +
                "Immortal launcher is running (which hides the Portal's own buttons).",
            style = MaterialTheme.typography.bodyMedium,
            color = PortalColors.OnSurface,
        )
        SegmentedChoice(
            options = listOf(
                Constants.IMMORTAL_NAV_AUTO to "Auto",
                Constants.IMMORTAL_NAV_ON to "On",
                Constants.IMMORTAL_NAV_OFF to "Off",
            ),
            selected = mode,
            onSelect = {
                mode = it
                appPreferences.immortalNavMode = it
            },
        )
    }
}

/** A simple 3-segment selector styled on the Portal palette. */
@Composable
private fun SegmentedChoice(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(PortalColors.SurfaceVariant),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) PortalColors.MetaBlue else androidx.compose.ui.graphics.Color.Transparent)
                    .pressable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) androidx.compose.ui.graphics.Color.White else PortalColors.OnSurface,
                )
            }
        }
    }
}

/** About portalfin: version, author, license, and a link to the repo. */
@Composable
private fun AboutSection(onOpenRepo: () -> Unit) {
    SectionCard(title = "About") {
        AboutRow("Version", BuildConfig.VERSION_NAME)
        AboutRow("Author", "Luke Hurd")
        AboutRow("License", "GPL-2.0-only")
        // Tappable repo link.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(8.dp))
                .pressable(onOpenRepo),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "GitHub", style = MaterialTheme.typography.bodyLarge, color = PortalColors.OnBackground)
            Spacer(Modifier.weight(1f))
            Text(
                text = "github.com/luke-hurd/portalfin",
                style = MaterialTheme.typography.bodyMedium,
                color = PortalColors.MetaBlue,
            )
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = PortalColors.OnBackground)
        Spacer(Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = PortalColors.OnSurface)
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

