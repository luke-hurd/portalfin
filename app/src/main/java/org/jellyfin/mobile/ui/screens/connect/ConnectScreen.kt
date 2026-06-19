package org.jellyfin.mobile.ui.screens.connect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.mobile.ui.utils.PortalColors
import org.jellyfin.mobile.MainViewModel
import org.jellyfin.mobile.R
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.ui.screens.HEADER_HEIGHT
import org.koin.compose.koinInject

@Composable
fun ConnectScreen(
    mainViewModel: MainViewModel,
    showExternalConnectionError: Boolean,
    activityEventHandler: ActivityEventHandler = koinInject(),
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Reserve the static portalfin header / Portal OSD band (the device
                // under-reports systemBars top on "aloha", so use the fixed reserve).
                .padding(top = HEADER_HEIGHT)
                .systemBarsPadding()
                .padding(horizontal = EDGE_PADDING),
        ) {
            Spacer(modifier = Modifier.height(EDGE_PADDING))
            ServerSelection(
                showExternalConnectionError = showExternalConnectionError,
                onConnected = { hostname ->
                    mainViewModel.switchServer(hostname)
                },
            )
            StyledTextButton(
                onClick = { activityEventHandler.emit(ActivityEvent.OpenDownloads) },
                text = stringResource(R.string.view_downloads),
                primary = false,
            )
        }
    }
}

// Shared edge padding so the auth screens line up with the rest of the app
// (home/library/profile all use 28dp).
internal val EDGE_PADDING = 28.dp

@Stable
@Composable
fun StyledTextButton(
    text: String,
    enabled: Boolean = true,
    primary: Boolean = true,
    onClick: () -> Unit,
) {
    if (primary) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(vertical = 4.dp),
            enabled = enabled,
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = PortalColors.MetaBlue,
                contentColor = PortalColors.OnBackground,
                // Stay blue (dimmed) when disabled so the primary action reads the
                // same on every screen — Connect is enabled (server prefilled) and
                // blue, so Sign In should look like the same button, not grey.
                disabledContainerColor = PortalColors.MetaBlue.copy(alpha = 0.4f),
                disabledContentColor = PortalColors.OnBackground.copy(alpha = 0.6f),
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        ) {
            Text(
                text = text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(vertical = 4.dp),
            enabled = enabled,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, PortalColors.SurfaceVariant),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = PortalColors.Surface,
                contentColor = PortalColors.OnBackground,
            ),
        ) {
            Text(
                text = text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}
