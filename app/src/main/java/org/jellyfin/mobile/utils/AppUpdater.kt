package org.jellyfin.mobile.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.mobile.BuildConfig
import org.json.JSONObject
import timber.log.Timber

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

    sealed interface InstallResult {
        data object Started : InstallResult
        /** Sent the user to the "install unknown apps" settings for portalfin. */
        data object NeedsPermission : InstallResult
        data object Failed : InstallResult
    }

    /** True if portalfin is currently allowed to install APKs. */
    fun canInstall(): Boolean =
        !AndroidVersion.isAtLeastO || context.packageManager.canRequestPackageInstalls()

    /**
     * If portalfin can't install yet, deep-link straight to ITS "install unknown
     * apps" toggle (not a generic settings page — that's the dead-end the system
     * dialog's "Settings" button lands on). The user enables it, comes back, taps
     * update again.
     */
    fun openInstallPermissionSettings() {
        val intent = if (AndroidVersion.isAtLeastO) {
            Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${BuildConfig.APPLICATION_ID}"),
            )
        } else {
            Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.w(e, "Couldn't open install-permission settings")
        }
    }

    /**
     * Download the latest `portalfin.apk` and install it via the [PackageInstaller]
     * session API. Caller should check [canInstall] first; if false, send the user
     * to [openInstallPermissionSettings].
     *
     * Unlike the legacy ACTION_INSTALL_PACKAGE intent (which just opens the system
     * installer UI and gives no result back — so a buggy installer screen can claim
     * "App not installed" even on success), the session API reports the real outcome
     * to [UpdateInstallReceiver], which we surface ourselves.
     */
    suspend fun downloadAndInstall(): InstallResult = withContext(Dispatchers.IO) {
        if (!canInstall()) return@withContext InstallResult.NeedsPermission
        try {
            val request = Request.Builder().url(LATEST_APK_URL).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext InstallResult.Failed
                val source = response.body?.byteStream() ?: return@withContext InstallResult.Failed
                val length = response.body?.contentLength() ?: -1L
                installViaSession(source, length)
            }
            InstallResult.Started
        } catch (e: Exception) {
            Timber.w(e, "Update download/install failed")
            InstallResult.Failed
        }
    }

    /** Stream the APK into a PackageInstaller session and commit it. */
    private fun installViaSession(apkStream: java.io.InputStream, length: Long) {
        val installer = context.packageManager.packageInstaller
        val params = android.content.pm.PackageInstaller.SessionParams(
            android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        )
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("portalfin.apk", 0, length).use { out ->
                apkStream.copyTo(out)
                session.fsync(out)
            }
            // The IntentSender fires UpdateInstallReceiver with the real status
            // (success / failure+message / pending-user-action confirm screen).
            val intent = Intent(context, UpdateInstallReceiver::class.java).apply {
                action = UpdateInstallReceiver.ACTION_INSTALL_STATUS
            }
            val flags = if (AndroidVersion.isAtLeastS) {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pending = android.app.PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
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
