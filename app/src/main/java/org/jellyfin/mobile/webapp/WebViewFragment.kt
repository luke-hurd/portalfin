package org.jellyfin.mobile.webapp

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.launch
import org.jellyfin.mobile.MainViewModel
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.bridge.ExternalPlayer
import org.jellyfin.mobile.bridge.MediaSegments
import org.jellyfin.mobile.bridge.NativeInterface
import org.jellyfin.mobile.bridge.NativePlayer
import org.jellyfin.mobile.data.entity.ServerEntity
import org.jellyfin.mobile.databinding.FragmentWebviewBinding
import org.jellyfin.mobile.setup.ConnectFragment
import org.jellyfin.mobile.utils.AndroidVersion
import org.jellyfin.mobile.utils.BackPressInterceptor
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.Constants.FRAGMENT_WEB_VIEW_EXTRA_SERVER
import org.jellyfin.mobile.utils.applyDefault
import org.jellyfin.mobile.utils.applyWindowInsetsAsMargins
import org.jellyfin.mobile.utils.dip
import org.jellyfin.mobile.utils.portalVideoFullscreen
import org.jellyfin.mobile.utils.extensions.getParcelableCompat
import org.jellyfin.mobile.utils.extensions.replaceFragment
import org.jellyfin.mobile.utils.fadeIn
import org.jellyfin.mobile.utils.isOutdated
import org.jellyfin.mobile.utils.requestNoBatteryOptimizations
import org.jellyfin.mobile.utils.runOnUiThread
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class WebViewFragment : Fragment(), BackPressInterceptor, JellyfinWebChromeClient.FileChooserListener {
    val appPreferences: AppPreferences by inject()
    private val mainViewModel: MainViewModel by activityViewModel()
    private val webappFunctionChannel: WebappFunctionChannel by inject()
    private lateinit var assetsPathHandler: AssetsPathHandler
    private lateinit var jellyfinWebViewClient: JellyfinWebViewClient
    private val nativePlayer: NativePlayer by inject()
    private lateinit var externalPlayer: ExternalPlayer
    private val mediaSegments: MediaSegments by inject()

    lateinit var server: ServerEntity
        private set

    // Optional deep-link path (e.g. "/web/#/details?id=…") appended to the
    // server URL on load. Set by the native home grid's interim tap handoff.
    private var startPath: String? = null
    private var connected = false
    private var restyleApplied = false
    private val timeoutRunnable = Runnable {
        handleError()
    }
    private val showLoadingContainerRunnable = Runnable {
        webViewBinding?.loadingContainer?.isVisible = true
    }
    private val restyleFallbackRunnable = Runnable {
        // If JS never signals (e.g. on a non-Jellyfin URL), reveal anyway
        revealWebView()
    }

    /** Called by portalfin-restyle.js once CSS + logo swap has been applied. */
    @Suppress("unused")
    inner class PortalFinBridge {
        @JavascriptInterface
        fun onRestyleApplied() {
            requireActivity().runOnUiThread { revealWebView() }
        }

        /**
         * Returns saved credentials as a JSON object so portalfin-restyle.js
         * can seed jellyfin-web's localStorage BEFORE the web client
         * bootstraps. Shape matches what jellyfin-web's connectionManager
         * stores at `localStorage.jellyfin_credentials`:
         *
         *   { Servers: [{ Id, Name, ManualAddress, UserId, AccessToken,
         *                  DateLastAccessed, LastConnectionMode }] }
         *
         * Returns "null" string if not authenticated.
         */
        /**
         * Called by portalfin-restyle.js when it detects jellyfin-web has
         * signed the user out (cleared its `jellyfin_credentials` localStorage).
         * Clears the native user record so MainActivity routes back to
         * LoginFragment.
         */
        @JavascriptInterface
        fun onSignedOut() {
            requireActivity().runOnUiThread {
                lifecycleScope.launch { mainViewModel.signOut() }
            }
        }

        /**
         * Called by the "Close app" button on the profile page. Fully tears the
         * app down (removes it from recents and ends the process) so the next
         * launch from the Portal Apps screen is a cold start. The login session
         * is left intact — this is a clean restart, not a sign-out.
         */
        @JavascriptInterface
        fun closeApp() {
            requireActivity().runOnUiThread {
                requireActivity().finishAndRemoveTask()
                // finishAndRemoveTask alone can leave the process warm; exit so
                // the relaunch rebuilds everything from scratch.
                Runtime.getRuntime().exit(0)
            }
        }

        /**
         * Called by portalfin-restyle.js when ambient mode engages/disengages.
         * Keeps the Portal display on during the slideshow so the device's
         * own ambient/dim doesn't black out our overlay.
         */
        @JavascriptInterface
        fun setAmbientActive(active: Boolean) {
            requireActivity().runOnUiThread {
                val window = requireActivity().window
                val webView = webViewBinding?.webView
                if (active) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    // The ambient slideshow is a DOM overlay INSIDE the WebView,
                    // which normally sits 64px below the top (Portal top-inset
                    // margin). That inset leaves a gray band + the Portal back/
                    // home pills uncovered above the overlay. Drop the inset so
                    // the WebView fills the screen and the slideshow covers it
                    // edge-to-edge; black the background behind it.
                    portalVideoFullscreen = true
                    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
                    webView?.setBackgroundColor(android.graphics.Color.BLACK)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    // Restore the Portal top inset (CLAUDE.md rule #3) + theme bg.
                    portalVideoFullscreen = false
                    window.setBackgroundDrawableResource(R.color.theme_background)
                    webView?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            }
        }

        @JavascriptInterface
        fun getCredentials(): String {
            val user = mainViewModel.userState.value.user ?: return "null"
            val server = mainViewModel.serverState.value.server ?: return "null"
            // Fetch the server's public GUID — jellyfin-web's credential
            // entry's `Id` field must match the actual server id, not our
            // local DB row id.
            val serverGuid = try {
                val url = java.net.URL("${server.hostname.trimEnd('/')}/System/Info/Public")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                org.json.JSONObject(body).optString("Id", "")
            } catch (_: Throwable) {
                ""
            }
            return org.json.JSONObject().apply {
                put("Servers", org.json.JSONArray().apply {
                    put(
                        org.json.JSONObject().apply {
                            put("Id", serverGuid)
                            put("Name", "Jellyfin")
                            put("ManualAddress", server.hostname)
                            put("LocalAddress", server.hostname)
                            put("UserId", user.userId.toString().replace("-", ""))
                            put("AccessToken", user.accessToken)
                            put("DateLastAccessed", System.currentTimeMillis())
                            put("LastConnectionMode", 2)
                        },
                    )
                })
            }.toString()
        }
    }

    private fun revealWebView() {
        if (restyleApplied) return
        restyleApplied = true
        val binding = webViewBinding ?: return
        binding.webView.removeCallbacks(restyleFallbackRunnable)
        // Cross-fade: instead of an instant container hide, animate the
        // loading-container's wordmark up + slightly smaller (toward the
        // header position the WebView shows), and fade the WebView in over
        // the same 240ms. Feels like the splash logo is dissolving into
        // the running app.
        val logo = binding.portalfinLogo
        val container = binding.loadingContainer
        binding.webView.alpha = 0f
        binding.webView.isVisible = true
        logo.animate()
            .translationY(-(logo.height / 2f))
            .scaleX(0.45f)
            .scaleY(0.45f)
            .alpha(0f)
            .setDuration(240L)
            .start()
        container.animate()
            .alpha(0f)
            .setDuration(240L)
            .withEndAction { container.isVisible = false }
            .start()
        binding.webView.animate()
            .alpha(1f)
            .setDuration(240L)
            .start()
    }

    // UI
    private var webViewBinding: FragmentWebviewBinding? = null

    // External file access
    private var fileChooserActivityLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        fileChooserCallback?.onReceiveValue(FileChooserParams.parseResult(result.resultCode, result.data))
    }
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        server = requireNotNull(requireArguments().getParcelableCompat(FRAGMENT_WEB_VIEW_EXTRA_SERVER)) {
            "Server entity has not been supplied!"
        }
        startPath = requireArguments().getString(Constants.FRAGMENT_WEB_VIEW_EXTRA_START_PATH)

        assetsPathHandler = AssetsPathHandler(requireContext())
        jellyfinWebViewClient = object : JellyfinWebViewClient(
            lifecycleScope,
            server,
            assetsPathHandler,
            mainViewModel,
        ) {
            override fun onConnectedToWebapp() {
                val webViewBinding = webViewBinding ?: return
                val webView = webViewBinding.webView
                webView.removeCallbacks(timeoutRunnable)
                webView.removeCallbacks(showLoadingContainerRunnable)
                connected = true
                // Don't reveal the WebView yet — wait for portalfin-restyle.js to
                // signal that CSS + logo swap is in place via PortalFinBridge.
                // Fallback in case the signal never arrives (non-Jellyfin pages).
                webView.postDelayed(restyleFallbackRunnable, RESTYLE_TIMEOUT_MS)
                requestNoBatteryOptimizations(webViewBinding.root)
            }

            override fun onErrorReceived() {
                handleError()
            }
        }
        externalPlayer = ExternalPlayer(requireContext(), this, requireActivity().activityResultRegistry)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentWebviewBinding.inflate(inflater, container, false).also { binding ->
            webViewBinding = binding
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val webView = webViewBinding!!.webView

        // Apply window insets
        webView.applyWindowInsetsAsMargins()

        // Setup exclusion rects for gestures
        if (AndroidVersion.isAtLeastQ) {
            @Suppress("MagicNumber")
            webView.doOnNextLayout {
                // Maximum allowed exclusion rect height is 200dp,
                // offsetting 100dp from the center in both directions
                // uses the maximum available space
                val verticalCenter = webView.measuredHeight / 2
                val offset = webView.resources.dip(100)

                // Arbitrary, currently 2x minimum touch target size
                val exclusionWidth = webView.resources.dip(96)

                webView.systemGestureExclusionRects = listOf(
                    Rect(
                        0,
                        verticalCenter - offset,
                        exclusionWidth,
                        verticalCenter + offset,
                    ),
                )
            }
        }

        // Setup WebView
        webView.initialize()

        webViewBinding!!.useDifferentServerButton.setOnClickListener {
            webView.removeCallbacks(timeoutRunnable)
            webView.stopLoading()
            webViewBinding!!.loadingContainer.isVisible = false
            onSelectServer(error = false)
        }

        // Process JS functions called from other components (e.g. the PlayerActivity)
        lifecycleScope.launch {
            for (function in webappFunctionChannel) {
                webView.evaluateJavascript(function, null)
            }
        }
    }

    override fun onInterceptBackPressed(): Boolean {
        return connected && webappFunctionChannel.goBack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webViewBinding = null
    }

    private fun WebView.initialize() {
        if (!appPreferences.ignoreWebViewChecks && isOutdated()) { // Check WebView version
            showOutdatedWebViewDialog(this)
            return
        }
        webViewClient = jellyfinWebViewClient
        webChromeClient = JellyfinWebChromeClient(this@WebViewFragment)
        settings.applyDefault()
        addJavascriptInterface(NativeInterface(requireContext()), "NativeInterface")
        addJavascriptInterface(nativePlayer, "NativePlayer")
        addJavascriptInterface(externalPlayer, "ExternalPlayer")
        addJavascriptInterface(mediaSegments, "MediaSegments")
        addJavascriptInterface(PortalFinBridge(), "PortalFinBridge")

        val startUrl = startPath?.let { path -> server.hostname.trimEnd('/') + path } ?: server.hostname
        loadUrl(startUrl)
        postDelayed(timeoutRunnable, Constants.INITIAL_CONNECTION_TIMEOUT)
        postDelayed(showLoadingContainerRunnable, Constants.SHOW_PROGRESS_BAR_DELAY)
    }

    private fun showOutdatedWebViewDialog(webView: WebView) {
        AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.dialog_web_view_outdated)
            setMessage(R.string.dialog_web_view_outdated_message)
            setCancelable(false)

            val webViewPackage = WebViewCompat.getCurrentWebViewPackage(context)
            if (webViewPackage != null) {
                val marketUri = Uri.Builder().apply {
                    scheme("market")
                    authority("details")
                    appendQueryParameter("id", webViewPackage.packageName)
                }.build()
                val referrerUri = Uri.Builder().apply {
                    scheme("android-app")
                    authority(context.packageName)
                }.build()

                val marketIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = marketUri
                    putExtra(Intent.EXTRA_REFERRER, referrerUri)
                }

                // Only show button if the intent can be resolved
                if (marketIntent.resolveActivity(context.packageManager) != null) {
                    setNegativeButton(R.string.dialog_button_check_for_updates) { _, _ ->
                        startActivity(marketIntent)
                        requireActivity().finishAfterTransition()
                    }
                }
            }
            if (AndroidVersion.isAtLeastN) {
                setPositiveButton(R.string.dialog_button_open_settings) { _, _ ->
                    startActivity(Intent(Settings.ACTION_WEBVIEW_SETTINGS))
                    Toast.makeText(context, R.string.toast_reopen_after_change, Toast.LENGTH_LONG).show()
                    requireActivity().finishAfterTransition()
                }
            }
            setNeutralButton(R.string.dialog_button_ignore) { _, _ ->
                appPreferences.ignoreWebViewChecks = true
                // Re-initialize
                webView.initialize()
            }
        }.show()
    }

    private fun onSelectServer(error: Boolean = false) = runOnUiThread {
        val activity = activity
        if (activity != null && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            val extras = when {
                error -> Bundle().apply {
                    putBoolean(Constants.FRAGMENT_CONNECT_EXTRA_ERROR, true)
                }
                else -> null
            }
            parentFragmentManager.replaceFragment<ConnectFragment>(extras)
        }
    }

    private fun handleError() {
        connected = false
        onSelectServer(error = true)
    }

    override fun onShowFileChooser(intent: Intent, filePathCallback: ValueCallback<Array<Uri>>) {
        fileChooserCallback = filePathCallback
        fileChooserActivityLauncher.launch(intent)
    }

    companion object {
        private const val RESTYLE_TIMEOUT_MS = 4000L
    }
}
