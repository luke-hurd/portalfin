package org.jellyfin.mobile.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.jellyfin.mobile.downloads.DownloadStatus
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID

@Entity(
    tableName = "download",
    indices = [Index(value = ["server_id"]), Index(value = ["user_id"]), Index(value = ["item_id"])],
    foreignKeys = [
        ForeignKey(
            entity = ServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["server_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0L,

    @ColumnInfo(name = "server_id") val serverId: Long,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "item_id") val itemId: UUID,

    @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "item") val item: BaseItemDto,

    // Actual on-disk filename of the transcoded media inside [path]. Null for
    // legacy rows downloaded before transcode-on-download (those used the
    // original remux filename derived from item.path). When set (e.g.
    // "<itemId>.mp4") playback and file lookup must use this instead.
    @ColumnInfo(name = "download_filename") val downloadFilename: String? = null,

    @ColumnInfo(name = "status") val status: DownloadStatus = DownloadStatus.QUEUED,

    // The DownloadQuality.intValue this was transcoded at (0=1080p, 1=720p,
    // 2=480p). Null for legacy rows from before the quality picker. Shown on the
    // Downloads screen so users can see each item's resolution.
    @ColumnInfo(name = "download_quality") val downloadQuality: Int? = null,

    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "modified_at") var modifiedAt: Long = System.currentTimeMillis(),
)
