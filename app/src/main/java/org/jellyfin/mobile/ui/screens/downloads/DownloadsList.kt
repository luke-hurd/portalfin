package org.jellyfin.mobile.ui.screens.downloads

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.downloads.DownloadStatus
import org.jellyfin.mobile.downloads.DownloadsViewModel
import org.jellyfin.mobile.ui.utils.PortalColors
import org.jellyfin.mobile.utils.lengthRecursive
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.koin.compose.koinInject

@Composable
fun DownloadsList(
    viewModel: DownloadsViewModel = viewModel(),
    contentPadding: PaddingValues = PaddingValues.Zero,
) {
    val downloads by viewModel.downloads.collectAsState()
    var downloadToRemove by remember { mutableStateOf<DownloadEntity?>(null) }

    if (downloadToRemove != null) {
        val context = LocalContext.current
        var keepLocalFiles by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { downloadToRemove = null },
            title = { Text(text = stringResource(R.string.download_remove)) },
            text = {
                Column {
                    val name = remember(downloadToRemove, context) {
                        downloadToRemove?.item?.getDownloadName(context).orEmpty()
                    }
                    Text(text = stringResource(R.string.download_remove_description, name))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Checkbox(
                            checked = keepLocalFiles,
                            onCheckedChange = { keepLocalFiles = it },
                        )
                        Text(text = stringResource(R.string.download_remove_keep_local))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadToRemove?.let { viewModel.removeDownload(it, deleteFiles = !keepLocalFiles) }
                        downloadToRemove = null
                    },
                ) {
                    Text(text = stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { downloadToRemove = null }) {
                    Text(text = stringResource(R.string.download_cancel))
                }
            },
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
    ) {
        items(
            downloads,
            key = DownloadEntity::id,
        ) { download ->
            DownloadItem(
                download,
                onOpen = { viewModel.openDownload(download) },
                onDownload = { viewModel.download(download) },
                onRemove = { downloadToRemove = download },
            )
        }
    }
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

    ListItem(
        modifier = modifier
            .heightIn(min = 52.dp)
            .combinedClickable(
                onClick = {
                    if (fileSize == null) {
                        onDownload()
                    } else {
                        onOpen()
                    }
                },
                onLongClick = { onRemove() },
            ),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            val name = remember(download.item, context) { download.item.getDownloadName(context) }
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = PortalColors.OnBackground,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
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
            if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED) {
                LinearProgressIndicator(color = PortalColors.MetaBlue)
            } else if (fileSize != null) {
                Text(
                    text = Formatter.formatShortFileSize(context, fileSize!!),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PortalColors.OnSurface,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            } else {
                Text(
                    text = stringResource(R.string.download_incomplete),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PortalColors.Warning,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
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
