package org.jellyfin.mobile.utils

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.provider.Settings.System.ACCELEROMETER_ROTATION
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.getSystemService
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.downloads.DownloadManager
import org.jellyfin.mobile.downloads.DownloadMethod
import org.jellyfin.mobile.downloads.DownloadQuality
import org.jellyfin.mobile.settings.ExternalPlayerPackage
import org.jellyfin.mobile.webapp.WebViewFragment
import org.jellyfin.sdk.model.serializer.toUUID
import org.koin.android.ext.android.get
import timber.log.Timber
import java.util.UUID
import kotlin.coroutines.resume

val isPortalDevice: Boolean
    get() = Build.MANUFACTURER.equals("Facebook", ignoreCase = true) ||
        Build.DEVICE.equals("aloha", ignoreCase = true)

const val IMMORTAL_LAUNCHER_PACKAGE = "com.immortal.launcher"

/**
 * True when the Immortal community launcher is the device's current home.
 *
 * Immortal replaces the Portal OEM launcher and — crucially — does NOT draw the
 * OEM system OSD band (the floating back/home pills at top-left). It still reports
 * `Build.DEVICE == "aloha"`, so device-id checks can't tell it apart. When this is
 * true, portalfin draws its own back/home affordance. See memory:
 * portal-top-inset-detection.
 *
 * Detection is resilient to the multi-launcher case: with both the OEM launcher
 * and Immortal installed and no launcher locked as the system default,
 * `resolveActivity(MATCH_DEFAULT_ONLY)` returns the chooser (no concrete
 * activity). So we check, in order:
 *   1. The resolved default HOME activity (covers a locked default).
 *   2. The user's *preferred* HOME activity via getPreferredActivities (covers
 *      "no locked default but a preferred launcher is set" — Immortal's case).
 */
fun Context.isImmortalLauncherDefault(): Boolean {
    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    // 1. Locked system default, if any.
    packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName
        ?.let { pkg -> if (pkg == IMMORTAL_LAUNCHER_PACKAGE) return true }

    // 2. Preferred HOME activity (set, but not locked as the single default).
    //    getPreferredActivities fills two parallel lists: filters[i] <-> activities[i].
    return try {
        val filters = mutableListOf<android.content.IntentFilter>()
        val activities = mutableListOf<android.content.ComponentName>()
        packageManager.getPreferredActivities(filters, activities, null)
        filters.indices.any { i ->
            filters[i].hasCategory(Intent.CATEGORY_HOME) &&
                activities[i].packageName == IMMORTAL_LAUNCHER_PACKAGE
        }
    } catch (_: Exception) {
        false
    }
}

/** True when the Immortal launcher is installed (regardless of whether it's the default home). */
fun Context.isImmortalLauncherInstalled(): Boolean = try {
    packageManager.getPackageInfo(IMMORTAL_LAUNCHER_PACKAGE, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

/**
 * Launch the Immortal launcher's home — used by portalfin's own "home" button as the
 * equivalent of the OEM home pill (drop back out to the launcher). Falls back to a
 * generic HOME intent if Immortal can't be launched directly.
 */
fun Context.launchImmortalHome() {
    val launch = packageManager.getLaunchIntentForPackage(IMMORTAL_LAUNCHER_PACKAGE)
    val intent = launch ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "Could not launch Immortal home")
    }
}

fun WebViewFragment.requestNoBatteryOptimizations(rootView: CoordinatorLayout) {
    if (isPortalDevice) return
    if (AndroidVersion.isAtLeastM) {
        val powerManager = requireContext().getSystemService(Activity.POWER_SERVICE) as PowerManager
        if (
            !appPreferences.ignoreBatteryOptimizations &&
            !powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)
        ) {
            Snackbar.make(rootView, R.string.battery_optimizations_message, Snackbar.LENGTH_INDEFINITE).apply {
                setAction(android.R.string.ok) {
                    try {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        Timber.e(e)
                    }

                    // Ignore after the user interacted with the snackbar at least once
                    appPreferences.ignoreBatteryOptimizations = true
                }
                show()
            }
        }
    }
}

suspend fun MainActivity.requestDownload(itemIds: Collection<UUID>) {
    if (itemIds.isEmpty()) return

    val appPreferences: AppPreferences = get()
    val downloadManager: DownloadManager = get()

    val permissionResult: Boolean = suspendCancellableCoroutine { continuation ->
        requestPermission("android.permission.POST_NOTIFICATIONS") { permissionsMap ->
            if (permissionsMap[Manifest.permission.POST_NOTIFICATIONS] == PackageManager.PERMISSION_GRANTED) {
                continuation.resume(true)
            } else {
                continuation.cancel(null)
            }
        }
    }

    // Portal is a stationary, always-on-WiFi device — there is no mobile/
    // roaming distinction to make. Skip jellyfin's "Allowed Network Types"
    // dialog entirely and default to MOBILE_AND_ROAMING, which maps to
    // WorkManager's NetworkType.CONNECTED (any connected network, no metered
    // restriction) so the download worker always runs over the Portal's WiFi.
    if (appPreferences.downloadMethod == null) {
        appPreferences.downloadMethod = DownloadMethod.MOBILE_AND_ROAMING
    }

    if (!permissionResult) return

    // Ask which quality to transcode-download at. Originals are multi-GB
    // remuxes; transcoding server-side gets a 2hr film down to ~1–2 GB.
    val quality: DownloadQuality = suspendCancellableCoroutine { continuation ->
        AlertDialog.Builder(this)
            .setTitle(R.string.download_quality_title)
            .setItems(
                arrayOf(
                    getString(R.string.download_quality_1080p),
                    getString(R.string.download_quality_720p),
                ),
            ) { _, which ->
                continuation.resume(if (which == 0) DownloadQuality.FULL_HD else DownloadQuality.HD)
            }
            .setOnCancelListener { continuation.cancel(null) }
            .show()
    }

    val server = mainViewModel.serverState.value.server ?: return
    val user = mainViewModel.userState.value.user ?: return
    downloadManager.enqueueItems(server, user, itemIds, quality)

    // The quality picker dismisses on tap with no feedback, so confirm the
    // download started and tell the user where to find it (Profile > Downloads).
    AlertDialog.Builder(this)
        .setTitle(R.string.download_started_title)
        .setMessage(R.string.download_started_message)
        .setPositiveButton(android.R.string.ok, null)
        .show()
}

fun Activity.isAutoRotateOn() = Settings.System.getInt(contentResolver, ACCELEROMETER_ROTATION, 0) == 1

fun PackageManager.isPackageInstalled(@ExternalPlayerPackage packageName: String) = try {
    packageName.isNotEmpty() && getApplicationInfo(packageName, 0).enabled
} catch (e: PackageManager.NameNotFoundException) {
    false
}

fun Context.createMediaNotificationChannel(notificationManager: NotificationManager) {
    if (AndroidVersion.isAtLeastO) {
        val notificationChannel = NotificationChannel(
            Constants.MEDIA_NOTIFICATION_CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Media notifications"
        }
        notificationManager.createNotificationChannel(notificationChannel)
    }
}

val Context.isLowRamDevice: Boolean
    get() = getSystemService<ActivityManager>()!!.isLowRamDevice

fun Uri.extractId(): String {
    val uri = toString()
    val idRegex = Regex("""/([a-f0-9]{32}|[a-f0-9-]{36})/""")
    val idResult = idRegex.find(uri)
    val itemId = idResult?.groups?.get(1)?.value.toString()
    var item = itemId.toUUID().toString()

    val subtitleRegex = Regex("""Subtitles/(\d+)/\d+/Stream.subrip|/(\d+).subrip""")
    val subtitleResult = subtitleRegex.find(uri)
    if (subtitleResult != null) {
        item += ":${subtitleResult.groups[1]?.value ?: subtitleResult.groups[2]?.value}"
    }

    return item
}
