package org.jellyfin.mobile.ui.screens.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.downloads.StorageInfo
import org.jellyfin.mobile.downloads.DownloadsViewModel
import org.jellyfin.mobile.ui.utils.PortalColors

// Match the other native screens (home/library) so Downloads feels consistent.
private val EDGE_PADDING = 28.dp

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = viewModel(),
    onBackPressed: () -> Unit = {},
    topContentPadding: Dp = 0.dp,
) {
    val downloads by viewModel.downloads.collectAsState()
    val storage by viewModel.storage.collectAsState()
    var downloadToRemove by remember { mutableStateOf<DownloadEntity?>(null) }

    RemoveDownloadDialog(
        download = downloadToRemove,
        onConfirm = { keepFiles ->
            downloadToRemove?.let { viewModel.removeDownload(it, deleteFiles = !keepFiles) }
            downloadToRemove = null
        },
        onDismiss = { downloadToRemove = null },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = EDGE_PADDING,
                end = EDGE_PADDING,
                top = topContentPadding + EDGE_PADDING,
                bottom = EDGE_PADDING,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(contentType = "title") {
                Text(
                    text = "Downloads",
                    style = MaterialTheme.typography.headlineSmall,
                    color = PortalColors.OnBackground,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            // Storage bar — device usage (iOS-style segmented bar).
            storage?.let { info ->
                item(contentType = "storage") {
                    StorageBar(info)
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (downloads.isEmpty()) {
                item(contentType = "empty") {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No downloads yet",
                            color = PortalColors.OnSurface,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            } else {
                items(downloads, key = DownloadEntity::id, contentType = { "download" }) { download ->
                    DownloadItem(
                        download = download,
                        onOpen = { viewModel.openDownload(download) },
                        onDownload = { viewModel.download(download) },
                        onRemove = { downloadToRemove = download },
                    )
                }
            }
        }
    }
}

/**
 * iOS-style segmented storage bar: a single rounded track split into
 * "this app's downloads" (Meta blue) + "other used" (grey), with the remainder
 * left as free space. A legend underneath labels each.
 */
@Composable
private fun StorageBar(info: StorageInfo) {
    val total = info.totalBytes.coerceAtLeast(1L)
    val downloadsFrac = info.downloadsBytes.toFloat() / total
    val otherFrac = info.otherUsedBytes.toFloat() / total

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(PortalColors.SurfaceVariant), // free space = track color
        ) {
            if (downloadsFrac > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(downloadsFrac)
                        .height(14.dp)
                        .background(PortalColors.MetaBlue),
                )
            }
            if (otherFrac > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(otherFrac / (1f - downloadsFrac).coerceAtLeast(0.0001f))
                        .height(14.dp)
                        .background(PortalColors.OnSurface.copy(alpha = 0.45f)),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(PortalColors.MetaBlue, "Downloads ${formatBytes(info.downloadsBytes)}")
            LegendDot(PortalColors.OnSurface.copy(alpha = 0.45f), "Other ${formatBytes(info.otherUsedBytes)}")
            LegendDot(PortalColors.SurfaceVariant, "Free ${formatBytes(info.freeBytes)}")
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall, color = PortalColors.OnSurface)
    }
}

internal fun formatBytes(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024 * 1024)
    if (gb >= 1.0) return "%.1f GB".format(gb)
    val mb = bytes / (1024.0 * 1024)
    return "%.0f MB".format(mb)
}
