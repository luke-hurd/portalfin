package org.jellyfin.mobile.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.mobile.BuildConfig
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/**
 * Self-update for sideloaded builds. portalfin ships as a fixed-name
 * `portalfin.apk` attached to every GitHub release, so the "latest" download URL
 * never changes — we check the latest release tag, and if it's newer than the
 * running build, download that APK and hand it to the system package installer.
 *
 * Not a silent update: Android always shows its own install confirmation, and the
 * first time the user must allow "install unknown apps" for portalfin. Installing
 * over the top keeps data/login (like `adb install -r`).
 */
class AppUpdater(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    sealed interface UpdateCheck {
        /** A newer release is available. [version] is the tag without the leading "v". */
        data class Available(val version: String) : UpdateCheck
        data object UpToDate : UpdateCheck
        data object Failed : UpdateCheck
    }

    /** Query the latest GitHub release and compare its version to this build. */
    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .build()
            val body = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext UpdateCheck.Failed
                response.body?.string() ?: return@withContext UpdateCheck.Failed
            }
            val tag = JSONObject(body).optString("tag_name").removePrefix("v")
            if (tag.isEmpty()) return@withContext UpdateCheck.Failed

            if (isNewer(remote = tag, local = BuildConfig.VERSION_NAME)) {
                UpdateCheck.Available(tag)
            } else {
                UpdateCheck.UpToDate
            }
        } catch (e: Exception) {
            Timber.w(e, "Update check failed")
            UpdateCheck.Failed
        }
    }

    /**
     * Download the latest `portalfin.apk` and launch the system installer.
     * Returns false if the download failed (the installer prompt itself is the
     * user's to accept). The APK goes to cacheDir/updates and is shared via
     * FileProvider.
     */
    suspend fun downloadAndInstall(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(LATEST_APK_URL).build()
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apk = File(updatesDir, "portalfin.apk")

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val sink = response.body?.byteStream() ?: return@withContext false
                apk.outputStream().use { out -> sink.copyTo(out) }
            }

            launchInstaller(apk)
            true
        } catch (e: Exception) {
            Timber.w(e, "Update download failed")
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun launchInstaller(apk: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", apk)
        // ACTION_INSTALL_PACKAGE targets the system package installer directly, so
        // the user doesn't get an "Open with" chooser (other launchers, e.g.
        // Immortal, also register as APK VIEW handlers). Deprecated but the simplest
        // reliable path on API 28; the PackageInstaller session API is the modern
        // alternative if this ever stops working.
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
        }
        context.startActivity(intent)
    }

    companion object {
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/luke-hurd/portalfin/releases/latest"
        private const val LATEST_APK_URL =
            "https://github.com/luke-hurd/portalfin/releases/latest/download/portalfin.apk"

        /**
         * Semantic-version compare of dotted numeric versions (e.g. "2.2.1").
         * A pre-release suffix on either side (after "-") is ignored for the core
         * comparison. Returns true when [remote] > [local].
         */
        fun isNewer(remote: String, local: String): Boolean {
            val r = parse(remote)
            val l = parse(local)
            for (i in 0 until maxOf(r.size, l.size)) {
                val rv = r.getOrElse(i) { 0 }
                val lv = l.getOrElse(i) { 0 }
                if (rv != lv) return rv > lv
            }
            return false
        }

        private fun parse(version: String): List<Int> =
            version.substringBefore('-')
                .split('.')
                .map { it.toIntOrNull() ?: 0 }
    }
}
