@file:Suppress("unused", "NOTHING_TO_INLINE")

package org.jellyfin.mobile.utils

import android.content.Context
import android.content.res.Resources
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.Window
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

inline fun Context.toast(@StringRes text: Int, duration: Int = Toast.LENGTH_SHORT) =
    Toast.makeText(this, text, duration).show()

inline fun Context.toast(text: CharSequence, duration: Int = Toast.LENGTH_SHORT) =
    Toast.makeText(this, text, duration).show()

inline fun LifecycleOwner.runOnUiThread(noinline block: suspend CoroutineScope.() -> Unit) {
    lifecycleScope.launch(Dispatchers.Main, block = block)
}

fun LayoutInflater.withThemedContext(context: Context, @StyleRes style: Int): LayoutInflater {
    return cloneInContext(ContextThemeWrapper(context, style))
}

// The Portal reserves a fixed band at the top of the screen for its system
// back/home overlay. We must keep the WebView pushed below it. We used to rely
// purely on the OS-reported system-bar inset, but the Portal can report
// insets.top == 0 (the overlay isn't always exposed as a top system bar),
// which let the WebView ride up under the icons — the recurring "lost top gap"
// bug. So on Portal we floor the top margin at this known reserve.
// See CLAUDE.md rule #3.
private const val PORTAL_TOP_RESERVE_DP = 64

// "Fill the screen, drop the Portal 64px top reserve." Set true in two cases:
//  1. Fullscreen video — the reserve would push the video down 64px (gray band)
//     and stop it centering.
//  2. Ambient slideshow — the overlay is a DOM layer INSIDE the WebView and
//     can't cover the 64px native gap (gray band + Portal back/home pills show
//     above it) unless the WebView fills the screen.
// Toggled by the fullscreen handler and setAmbientActive. Outside those, the
// reserve is restored (CLAUDE.md rule #3 — the gap is sacred for normal browsing).
var portalVideoFullscreen: Boolean = false
    set(value) {
        field = value
        pendingInsetReapply?.invoke()
    }
private var pendingInsetReapply: (() -> Unit)? = null

fun View.applyWindowInsetsAsMargins() {
    val density = resources.displayMetrics.density
    val isPortal = android.os.Build.DEVICE == "aloha"
    val portalTopReservePx = (PORTAL_TOP_RESERVE_DP * density).toInt()
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        // In fullscreen video / ambient, the WebView must fill the WHOLE screen
        // (top margin 0) so the video centers and the ambient overlay covers the
        // top band + Portal pills. Using insets.top here would still leave the
        // OS-reported ~60px gap — force 0.
        val topMargin = when {
            isPortal && portalVideoFullscreen -> 0
            isPortal -> maxOf(insets.top, portalTopReservePx)
            else -> insets.top
        }
        updateLayoutParams<MarginLayoutParams> {
            setMargins(insets.left, topMargin, insets.right, insets.bottom)
        }
        windowInsets
    }
    // Allow the fullscreen toggle to re-run inset application on this view.
    pendingInsetReapply = { ViewCompat.requestApplyInsets(this) }
}

fun View.fadeIn() {
    alpha = 0f
    isVisible = true
    animate().apply {
        alpha(1f)
        @Suppress("MagicNumber")
        duration = 300L
        interpolator = LinearOutSlowInInterpolator()
        withLayer()
    }
}

inline fun Resources.dip(px: Int) = (px * displayMetrics.density).toInt()

inline var Window.brightness: Float
    get() = attributes.screenBrightness
    set(value) {
        attributes = attributes.apply {
            screenBrightness = value
        }
    }
