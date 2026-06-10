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

    if (permissionResult) {
        val server = mainViewModel.serverState.value.server ?: return
        val user = mainViewModel.userState.value.user ?: return
        downloadManager.enqueueItems(server, user, itemIds)
    }
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
