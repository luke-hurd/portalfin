package org.jellyfin.mobile.downloads

/**
 * Quality presets for transcode-on-download. The Portal screen is 1280×800,
 * so 1080p is the practical ceiling. Bitrates are chosen to land a ~2hr movie
 * around 1–2 GB instead of the multi-GB original remux.
 *
 * Downloads are transcoded server-side to H.264/AAC in an MP4 container so the
 * resulting file direct-plays everywhere.
 */
enum class DownloadQuality(
    val intValue: Int,
    val maxHeight: Int,
    val videoBitRate: Int,
    val audioBitRate: Int,
    /** Short UI label. */
    val label: String,
    /** Approx bytes per hour at this preset (video+audio), for free-space checks. */
    val bytesPerHour: Long,
) {
    // ~2 Mbps video → ~1.5 GB for a 2hr film
    FULL_HD(0, maxHeight = 1080, videoBitRate = 2_000_000, audioBitRate = 128_000, label = "1080p", bytesPerHour = 960L * 1024 * 1024),

    // ~1 Mbps video → ~1 GB for a 2hr film
    HD(1, maxHeight = 720, videoBitRate = 1_000_000, audioBitRate = 128_000, label = "720p", bytesPerHour = 510L * 1024 * 1024),

    // ~0.5 Mbps video → ~0.5 GB for a 2hr film. The Portal panel is small, so
    // 480p still looks fine and stretches the limited on-device storage.
    SD(2, maxHeight = 480, videoBitRate = 500_000, audioBitRate = 96_000, label = "480p", bytesPerHour = 270L * 1024 * 1024),
    ;

    companion object {
        val DEFAULT = FULL_HD

        fun fromInt(value: Int): DownloadQuality? = entries.find { it.intValue == value }
    }
}
