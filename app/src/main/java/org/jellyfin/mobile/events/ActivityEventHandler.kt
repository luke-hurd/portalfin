package org.jellyfin.mobile.events

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.bridge.JavascriptCallback
import org.jellyfin.mobile.downloads.DownloadsFragment
import org.jellyfin.mobile.player.ui.PlayerFragment
import org.jellyfin.mobile.player.ui.PlayerFullscreenHelper
import org.jellyfin.mobile.settings.SettingsFragment
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.extensions.addFragment
import org.jellyfin.mobile.utils.portalVideoFullscreen
import org.jellyfin.mobile.utils.requestDownload
import org.jellyfin.mobile.webapp.WebViewFragment
import org.jellyfin.mobile.webapp.WebappFunctionChannel
import timber.log.Timber

class ActivityEventHandler(
    private val webappFunctionChannel: WebappFunctionChannel,
) {
    private val eventsFlow = MutableSharedFlow<ActivityEvent>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    fun MainActivity.subscribe() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                eventsFlow.collect { event ->
                    handleEvent(event)
                }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun MainActivity.handleEvent(event: ActivityEvent) {
        when (event) {
            is ActivityEvent.ChangeFullscreen -> {
                val fullscreenHelper = PlayerFullscreenHelper(window)
                // The web video renders INTO the WebView (no separate SurfaceView
                // on this device — verified via SurfaceFlinger). The page CSS makes
                // html/body transparent during video, so the letterbox bars show
                // whatever is BEHIND the transparent page = the WebView's own
                // native background + its container. Both default to the gray
                // theme_background (#1A1A1A) — THAT is the gray bars the user sees
                // (adb screencap does NOT reveal it; trust the device). So we must
                // black out the WebView + its parent container, not just the window.
                val webView = supportFragmentManager.findFragmentById(R.id.fragment_container)
                    ?.let { it as? WebViewFragment }
                    ?.view?.findViewById<View>(R.id.web_view)
                if (event.isFullscreen) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    fullscreenHelper.enableFullscreen()
                    window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
                    webView?.setBackgroundColor(Color.BLACK)
                    (webView?.parent as? View)?.setBackgroundColor(Color.BLACK)
                    // Drop the Portal 64px top reserve so the WebView fills the
                    // screen and the video centers vertically (no gray top gap).
                    portalVideoFullscreen = true
                } else {
                    // Reset screen orientation
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    fullscreenHelper.disableFullscreen()
                    // Restore the Portal top reserve (CLAUDE.md rule #3).
                    portalVideoFullscreen = false
                    // Reset window + WebView background color
                    window.setBackgroundDrawableResource(R.color.theme_background)
                    webView?.setBackgroundColor(Color.TRANSPARENT)
                    (webView?.parent as? View)?.setBackgroundResource(R.color.theme_background)
                }
            }
            is ActivityEvent.LaunchNativePlayer -> {
                val args = Bundle().apply {
                    putParcelable(Constants.EXTRA_MEDIA_PLAY_OPTIONS, event.playOptions)
                }
                supportFragmentManager.addFragment<PlayerFragment>(args)
            }
            is ActivityEvent.OpenUrl -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, event.uri.toUri())
                    if (event.grantReadPermission) intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Timber.e("openIntent: %s", e.message)
                }
            }
            is ActivityEvent.DownloadItems -> {
                lifecycleScope.launch {
                    with(event) { requestDownload(itemIds) }
                }
            }
            ActivityEvent.OpenDownloads -> {
                supportFragmentManager.addFragment<DownloadsFragment>()
            }
            is ActivityEvent.CastMessage -> {
                val action = event.action
                chromecast.execute(
                    action,
                    event.args,
                    object : JavascriptCallback() {
                        override fun callback(keep: Boolean, err: String?, result: String?) {
                            webappFunctionChannel.call(
                                """window.NativeShell.castCallback("$action", $keep, $err, $result);""",
                            )
                        }
                    },
                )
            }
            ActivityEvent.RequestBluetoothPermission -> {
                lifecycleScope.launch {
                    bluetoothPermissionHelper.requestBluetoothPermissionIfNecessary()
                }
            }
            ActivityEvent.OpenSettings -> {
                supportFragmentManager.addFragment<SettingsFragment>()
            }
            ActivityEvent.SelectServer -> {
                mainViewModel.resetServer()
            }
            ActivityEvent.ExitApp -> {
                if (serviceBinder?.isPlaying == true) {
                    moveTaskToBack(false)
                } else {
                    finish()
                }
            }
        }
    }

    fun emit(event: ActivityEvent) {
        eventsFlow.tryEmit(event)
    }
}
