package org.jellyfin.mobile.ui.screens.downloads

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.downloads.DownloadStatus
import org.jellyfin.mobile.ui.utils.PortalColors
import org.jellyfin.mobile.utils.lengthRecursive
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.koin.compose.koinInject

/** Remove-download confirm dialog (null download = hidden). */
@Composable
fun RemoveDownloadDialog(
    download: DownloadEntity?,
    onConfirm: (keepFiles: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    if (download == null) return
    val context = LocalContext.current
    var keepLocalFiles by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.download_remove)) },
        text = {
            Column {
                val name = remember(download, context) { download.item.getDownloadName(context) }
                Text(text = stringResource(R.string.download_remove_description, name))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Checkbox(checked = keepLocalFiles, onCheckedChange = { keepLocalFiles = it })
                    Text(text = stringResource(R.string.download_remove_keep_local))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(keepLocalFiles) }) {
                Text(text = stringResource(R.string.yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.download_cancel)) }
        },
    )
}

@Composable
fun DownloadItem(
    download: DownloadEntity,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val apiClient: ApiClient = koinInject()
    val storageManager: StorageManager = koinInject()

    val fileSize by produceState<Long?>(initialValue = 0L, download) {
        value = withContext(Dispatchers.IO) {
            val itemLocation = storageManager.getStorageLocation().findFile(download.path)
            itemLocation?.lengthRecursive()
        }
    }

    val downloading = download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED
    val qualityLabel = download.downloadQuality
        ?.let { org.jellyfin.mobile.downloads.DownloadQuality.fromInt(it)?.label }

    ListItem(
        modifier = modifier
            .heightIn(min = 56.dp)
            .combinedClickable(
                onClick = { if (fileSize == null) onDownload() else onOpen() },
                onLongClick = { onRemove() },
            ),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            // Title + inline progress: while downloading the progress bar sits on
            // the SAME row as the title (per request). Transcoded streams send no
            // total length, so the bar is indeterminate (no fake %).
            val name = remember(download.item, context) { download.item.getDownloadName(context) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PortalColors.OnBackground,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (downloading) {
                    LinearProgressIndicator(
                        color = PortalColors.MetaBlue,
                        trackColor = PortalColors.SurfaceVariant,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .width(72.dp),
                    )
                }
            }
        },
        leadingContent = {
            val maxSize = LocalResources.current.getDimensionPixelSize(R.dimen.movie_thumbnail_list_size)
            val url = remember(apiClient, download.itemId, maxSize) {
                apiClient.imageApi.getItemImageUrl(
                    itemId = download.itemId,
                    imageType = ImageType.PRIMARY,
                    maxWidth = maxSize,
                    maxHeight = maxSize,
                )
            }

            AsyncImage(
                model = url,
                placeholder = painterResource(R.drawable.ic_local_movies_white_64),
                fallback = painterResource(R.drawable.ic_local_movies_white_64),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
        },
        supportingContent = {
            // Quality (480p/720p) · size, or a status when not yet complete.
            when {
                downloading -> Text(
                    text = listOfNotNull(qualityLabel, "Downloading…").joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PortalColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                fileSize != null -> Text(
                    text = listOfNotNull(qualityLabel, Formatter.formatShortFileSize(context, fileSize!!))
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PortalColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                else -> Text(
                    text = stringResource(R.string.download_incomplete),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PortalColors.Warning,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        // Visible delete affordance. Long-press still works, but a tappable
        // trash button is discoverable on the Portal's touch-only UI.
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_white_24dp),
                    contentDescription = stringResource(R.string.download_remove),
                    tint = PortalColors.OnSurface,
                )
            }
        },
    )
}

private fun BaseItemDto.getDownloadName(context: Context) = buildString {
    val name = if (
        type in arrayOf(BaseItemKind.PROGRAM, BaseItemKind.RECORDING) &&
        (isSeries == true || !episodeTitle.isNullOrEmpty())
    ) {
        episodeTitle
    } else {
        name
    }

    val extraInfo = when (type) {
        BaseItemKind.TV_CHANNEL if !channelNumber.isNullOrEmpty() -> channelNumber
        BaseItemKind.EPISODE if parentIndexNumber == 0 -> context.getString(R.string.special_episode)
        in arrayOf(BaseItemKind.EPISODE, BaseItemKind.RECORDING) if indexNumber != null && parentIndexNumber != null ->
            "S$parentIndexNumber:E${indexNumber}${indexNumberEnd?.let { n -> "-$n" } ?: ""}"
        else -> ""
    }

    listOf(seriesName, extraInfo, name)
        .filter { str -> !str.isNullOrEmpty() }
        .joinTo(this, separator = " - ")

    if (type == BaseItemKind.MOVIE && productionYear != null) {
        append(" ($productionYear)")
    } else if (premiereDate != null) {
        append(" (${premiereDate!!.year})")
    }
}.ifEmpty { name.orEmpty() }
