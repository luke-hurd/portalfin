package org.jellyfin.mobile

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.OrientationEventListener
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.cast.Chromecast
import org.jellyfin.mobile.player.cast.IChromecast
import org.jellyfin.mobile.player.ui.PlayerFragment
import org.jellyfin.mobile.setup.ConnectFragment
import org.jellyfin.mobile.setup.LoginFragment
import org.jellyfin.mobile.ui.screens.detail.DetailFragment
import org.jellyfin.mobile.ui.screens.home.HomeFragment
import org.jellyfin.mobile.ui.screens.library.LibraryFragment
import org.jellyfin.mobile.downloads.DownloadsFragment
import org.jellyfin.mobile.ui.screens.profile.ProfileFragment
import org.jellyfin.mobile.ui.screens.search.SearchFragment
import org.jellyfin.mobile.ui.screens.season.SeasonFragment
import org.jellyfin.mobile.utils.AndroidVersion
import org.jellyfin.mobile.utils.BackPressInterceptor
import org.jellyfin.mobile.utils.BluetoothPermissionHelper
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.PermissionRequestHelper
import org.jellyfin.mobile.utils.isImmortalLauncherDefault
import org.jellyfin.mobile.utils.launchImmortalHome
import org.jellyfin.mobile.utils.SmartOrientationListener
import org.jellyfin.mobile.utils.extensions.addFragment
import org.jellyfin.mobile.utils.extensions.addFragmentAnimated
import org.jellyfin.mobile.utils.extensions.replaceFragment
import org.jellyfin.mobile.utils.isWebViewSupported
import org.jellyfin.mobile.webapp.RemotePlayerService
import org.jellyfin.mobile.webapp.WebViewFragment
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.androidx.fragment.android.setupKoinFragmentFactory
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity() {
    private val activityEventHandler: ActivityEventHandler = get()
    val mainViewModel: MainViewModel by viewModel()
    val bluetoothPermissionHelper: BluetoothPermissionHelper = BluetoothPermissionHelper(this, get())
    val chromecast: IChromecast = Chromecast()
    private val permissionRequestHelper: PermissionRequestHelper by inject()
    private val appPreferences: AppPreferences by inject()
    private val appUpdater: org.jellyfin.mobile.utils.AppUpdater by inject()

    var serviceBinder: RemotePlayerService.ServiceBinder? = null
        private set
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
            serviceBinder = binder as? RemotePlayerService.ServiceBinder
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            serviceBinder = null
        }
    }

    private val orientationListener: OrientationEventListener by lazy { SmartOrientationListener(this) }

    // --- Ambient screensaver ----------------------------------------------
    // After [AMBIENT_IDLE_MS] with no user interaction, show a full-screen native
    // ambient slideshow (see ui/screens/ambient). Any touch dismisses it. The
    // timer is driven off onUserInteraction() and paused while a video plays.
    private val ambientHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val ambientRunnable = Runnable { showAmbient() }
    private var ambientShown = false

    /**
     * Passes back press events onto the currently visible [Fragment] if it implements the [BackPressInterceptor] interface.
     *
     * If the current fragment does not implement [BackPressInterceptor] or has decided not to intercept the event
     * (see result of [BackPressInterceptor.onInterceptBackPressed]), the topmost backstack entry will be popped.
     *
     * If there is no topmost backstack entry, the event will be passed onto the dispatcher's fallback handler.
     */
    private val onBackPressedCallback: OnBackPressedCallback.() -> Unit = callback@{
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment is BackPressInterceptor && currentFragment.onInterceptBackPressed()) {
            // Top fragment handled back press
            return@callback
        }

        // This is the same default action as in Activity.onBackPressed
        if (!supportFragmentManager.isStateSaved && supportFragmentManager.popBackStackImmediate()) {
            // Removed fragment from back stack
            return@callback
        }

        // Let the system handle the back press
        isEnabled = false
        // Make sure that we *really* call the fallback handler
        assert(!onBackPressedDispatcher.hasEnabledCallbacks()) {
            "MainActivity should be the lowest onBackPressCallback"
        }
        onBackPressedDispatcher.onBackPressed()
        isEnabled = true // re-enable callback in case activity isn't finished
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Classic launch splash: the launch theme's windowBackground (dark +
        // centered wordmark) is shown by the system for the whole cold start.
        // Switch to the real theme now that the activity is starting.
        setTheme(R.style.AppTheme)
        setupKoinFragmentFactory()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupPortalHeader()
        refreshImmortalNavState()

        // Check WebView support
        if (!isWebViewSupported()) {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.dialog_web_view_not_supported)
                setMessage(R.string.dialog_web_view_not_supported_message)
                setCancelable(false)
                if (AndroidVersion.isAtLeastN) {
                    setNeutralButton(R.string.dialog_button_open_settings) { _, _ ->
                        startActivity(Intent(Settings.ACTION_WEBVIEW_SETTINGS))
                        Toast.makeText(context, R.string.toast_reopen_after_change, Toast.LENGTH_LONG).show()
                        finishAfterTransition()
                    }
                }
                setNegativeButton(R.string.dialog_button_close_app) { _, _ ->
                    finishAfterTransition()
                }
            }.show()
            return
        }

        // Bind player service
        bindService(Intent(this, RemotePlayerService::class.java), serviceConnection, Service.BIND_AUTO_CREATE)

        // Subscribe to activity events
        with(activityEventHandler) { subscribe() }

        // Self-update: check GitHub for a newer release on launch (best-effort,
        // silent on failure/up-to-date). See AppUpdater.
        checkForUpdate()

        // Load UI — drive routing off both server and user state so we can
        // show our native LoginFragment between Connect and WebView.
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                mainViewModel.serverState,
                mainViewModel.userState,
            ) { server, user -> server to user }.collectLatest { (server, user) ->
                lifecycle.withStarted {
                    handleRouting(server, user)
                    // Auth state just changed: (re)arm the ambient idle timer now
                    // that we may be logged in. Without this, the onCreate/onResume
                    // scheduleAmbient() ran while still logged out, returned early,
                    // and nothing re-armed it once login completed.
                    scheduleAmbient()
                }
            }
        }

        // Handle back presses
        onBackPressedDispatcher.addCallback(this, onBackPressed = onBackPressedCallback)

        // Setup Chromecast
        chromecast.initializePlugin(this)
    }

    override fun onStart() {
        super.onStart()
        orientationListener.enable()
    }

    private fun handleRouting(serverState: ServerState, userState: UserState) {
        with(supportFragmentManager) {
            val currentFragment = findFragmentById(R.id.fragment_container)
            when (serverState) {
                ServerState.Pending -> {
                    // Wait for server load before routing.
                }
                is ServerState.Unset -> {
                    if (currentFragment !is ConnectFragment) {
                        replaceFragment<ConnectFragment>()
                    }
                }
                is ServerState.Available -> when (userState) {
                    UserState.Pending -> {
                        // Wait for user load.
                    }
                    UserState.Unset -> {
                        if (currentFragment !is LoginFragment) {
                            replaceFragment<LoginFragment>()
                        }
                    }
                    is UserState.Available -> routeAuthenticated(currentFragment, serverState.server)
                }
            }
        }
        // replaceFragment isn't a back-stack op, so sync the header here too.
        supportFragmentManager.executePendingTransactions()
        updatePortalHeaderVisibility()
    }

    /**
     * Authenticated landing route — always the native Compose home now. (The old
     * WebView home + its toggle were removed; the WebView only backs the few
     * un-ported deep-link routes via [openWebViewAt].)
     */
    private fun routeAuthenticated(currentFragment: Fragment?, server: org.jellyfin.mobile.data.entity.ServerEntity) {
        if (currentFragment !is HomeFragment) {
            supportFragmentManager.replaceFragment<HomeFragment>()
        }
    }

    /**
     * Open the WebView at a deep-link path under the current server. Used by the
     * native home grid's interim tap handoff (TODO(native-detail): replace with a
     * native detail screen). Added to the back stack so back returns to the
     * native home.
     */
    fun openWebViewAt(path: String) {
        val server = mainViewModel.serverState.value.server ?: return
        supportFragmentManager.addFragment<WebViewFragment>(
            Bundle().apply {
                putParcelable(Constants.FRAGMENT_WEB_VIEW_EXTRA_SERVER, server)
                putString(Constants.FRAGMENT_WEB_VIEW_EXTRA_START_PATH, path)
            },
        )
    }

    /** Dive into a library — push the native library grid onto the back stack. */
    fun openLibrary(library: org.jellyfin.sdk.model.api.BaseItemDto) {
        supportFragmentManager.addFragmentAnimated<LibraryFragment>(LibraryFragment.args(library))
    }

    /** Open an item's native detail screen — pushed onto the back stack. */
    fun openDetail(item: org.jellyfin.sdk.model.api.BaseItemDto) {
        supportFragmentManager.addFragmentAnimated<DetailFragment>(DetailFragment.args(item))
    }

    /** Open a season's episode list — pushed onto the back stack. */
    fun openSeason(season: org.jellyfin.sdk.model.api.BaseItemDto) {
        supportFragmentManager.addFragmentAnimated<SeasonFragment>(SeasonFragment.args(season))
    }

    /** Return to the native home — pop everything above it off the back stack. */
    fun popToHome() {
        supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    /** Open the native profile/settings screen (from the home Settings card). */
    fun openSettings() {
        supportFragmentManager.addFragmentAnimated<ProfileFragment>()
    }

    /** Open the native search screen (REST API search, no WebView). */
    fun openSearch() {
        supportFragmentManager.addFragmentAnimated<SearchFragment>()
    }

    /** Open the native Downloads screen (from the home Downloads icon). */
    fun openDownloads() {
        supportFragmentManager.addFragmentAnimated<DownloadsFragment>()
    }

    /**
     * The portalfin header is an Activity-level overlay (above the fragment
     * container) so it stays STATIC while fragments animate beneath it. Native
     * screens call [showPortalHeader] to reveal it.
     */
    private fun setupPortalHeader() {
        val header = findViewById<androidx.compose.ui.platform.ComposeView>(R.id.portal_header)
        header.setContent {
            org.jellyfin.mobile.ui.utils.AppTheme {
                org.jellyfin.mobile.ui.screens.PortalHeader(
                    onLogoClick = { popToHome() },
                    showImmortalNav = immortalNavState.value,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onHome = { launchImmortalHome() },
                )
            }
        }
        // Keep header visibility in sync with whatever fragment is on top —
        // covers routing, openLibrary, and back/pop in one place.
        supportFragmentManager.addOnBackStackChangedListener { updatePortalHeaderVisibility() }
    }

    /**
     * Whether to draw portalfin's own back/home nav. Pure auto-detection: the
     * Immortal launcher force-hides the OEM OSD (globally, via policy_control set
     * at install — see memory portalfin-immortal-nav), so under Immortal our pills
     * are the only nav; on the stock OEM OS the system OSD shows and we draw
     * nothing. No user toggle — detection is reliable. Re-evaluated on resume in
     * case the launcher changed. Backed by a Compose state so the header recomposes.
     *
     * NOTE: we deliberately do NOT touch the system bars here. The OEM OSD is shown
     * by the OS by default; suppressing it during browsing was a regression.
     */
    private val immortalNavState = androidx.compose.runtime.mutableStateOf(false)

    private fun refreshImmortalNavState() {
        immortalNavState.value = isImmortalLauncherDefault()
    }

    // --- Self-update -------------------------------------------------------

    /** Silent on-launch check; prompts only if a newer release exists. */
    private fun checkForUpdate() {
        lifecycleScope.launch {
            val result = appUpdater.check()
            if (result is org.jellyfin.mobile.utils.AppUpdater.UpdateCheck.Available) {
                lifecycle.withStarted { promptUpdate(result.version) }
            }
        }
    }

    /**
     * Manual check (from Profile). [onResult] reports back so the UI can show a
     * "you're up to date" / "check failed" message; an available update prompts here.
     */
    fun checkForUpdateManually(onResult: (org.jellyfin.mobile.utils.AppUpdater.UpdateCheck) -> Unit) {
        lifecycleScope.launch {
            val result = appUpdater.check()
            if (result is org.jellyfin.mobile.utils.AppUpdater.UpdateCheck.Available) {
                promptUpdate(result.version)
            }
            onResult(result)
        }
    }

    private fun promptUpdate(version: String) {
        AlertDialog.Builder(this).apply {
            setTitle("Update available")
            setMessage("portalfin $version is available. Download and install it now?")
            setPositiveButton("Update") { _, _ ->
                // First time: portalfin needs the "install unknown apps" permission.
                // Send the user straight to its toggle instead of failing.
                if (!appUpdater.canInstall()) {
                    promptInstallPermission()
                    return@setPositiveButton
                }
                Toast.makeText(this@MainActivity, "Downloading update…", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    when (appUpdater.downloadAndInstall()) {
                        org.jellyfin.mobile.utils.AppUpdater.InstallResult.NeedsPermission -> promptInstallPermission()
                        org.jellyfin.mobile.utils.AppUpdater.InstallResult.Failed ->
                            Toast.makeText(this@MainActivity, "Update download failed", Toast.LENGTH_LONG).show()
                        org.jellyfin.mobile.utils.AppUpdater.InstallResult.Started -> Unit
                    }
                }
            }
            setNegativeButton("Later", null)
        }.show()
    }

    /** First-update gate: explain + deep-link to portalfin's install-sources toggle. */
    private fun promptInstallPermission() {
        AlertDialog.Builder(this).apply {
            setTitle("Allow updates")
            setMessage(
                "To install updates, allow portalfin to install apps. " +
                    "Turn on \"Allow from this source\" on the next screen, then tap Update again.",
            )
            setPositiveButton("Open settings") { _, _ -> appUpdater.openInstallPermissionSettings() }
            setNegativeButton("Cancel", null)
        }.show()
    }

    /** Header is shown only on the native home/library screens. */
    fun updatePortalHeaderVisibility() {
        val top = supportFragmentManager.findFragmentById(R.id.fragment_container)
        val show = top is HomeFragment || top is LibraryFragment ||
            top is DetailFragment || top is ProfileFragment || top is SeasonFragment ||
            top is SearchFragment || top is DownloadsFragment ||
            top is ConnectFragment || top is LoginFragment
        findViewById<androidx.compose.ui.platform.ComposeView>(R.id.portal_header).visibility =
            if (show) android.view.View.VISIBLE else android.view.View.GONE
    }

    // --- Ambient screensaver ----------------------------------------------

    /** Reset the idle countdown on every touch; dismiss ambient if it's up. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        if (ambientShown) {
            hideAmbient()
        } else {
            scheduleAmbient()
        }
    }

    /** (Re)arm the idle timer unless a video is playing. */
    private fun scheduleAmbient() {
        ambientHandler.removeCallbacks(ambientRunnable)
        if (isVideoPlaying() || !isLoggedIn()) return
        ambientHandler.postDelayed(ambientRunnable, AMBIENT_IDLE_MS)
    }

    private fun isVideoPlaying(): Boolean = supportFragmentManager.fragments.any { it is PlayerFragment && it.isVisible }

    /**
     * The ambient slideshow pulls backdrops from the signed-in Jellyfin server, so
     * it only makes sense once we have BOTH a server and a logged-in user. On the
     * Connect/Login screens there's no server to fetch from — engaging there gives
     * an empty (image-less) screensaver. Gate on full auth.
     */
    private fun isLoggedIn(): Boolean =
        mainViewModel.serverState.value is ServerState.Available &&
            mainViewModel.userState.value is UserState.Available

    private fun showAmbient() {
        if (ambientShown || isVideoPlaying() || !isLoggedIn()) return
        // Never engage while backgrounded — the timer could otherwise fire just as
        // the app leaves the foreground, and the user would return to a running
        // screensaver (a past regression). RESUMED == we're actually on screen.
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return
        ambientShown = true
        val overlay = findViewById<androidx.compose.ui.platform.ComposeView>(R.id.ambient_overlay)
        overlay.setContent {
            org.jellyfin.mobile.ui.utils.AppTheme {
                org.jellyfin.mobile.ui.screens.ambient.AmbientScreen()
            }
        }
        overlay.visibility = android.view.View.VISIBLE
        // Keep the Portal display awake while the slideshow runs (it otherwise
        // dims/sleeps mid-rotation). Cleared in hideAmbient().
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Hide the Portal's system OSD band (back/home pills) so the slideshow is
        // truly edge-to-edge — same immersive trick the video player uses.
        setSystemBarsHidden(true)
    }

    private fun hideAmbient() {
        if (!ambientShown) return
        ambientShown = false
        val overlay = findViewById<androidx.compose.ui.platform.ComposeView>(R.id.ambient_overlay)
        overlay.visibility = android.view.View.GONE
        // Dispose the composition so its timers/coroutines stop.
        overlay.setContent {}
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Restore the Portal OSD for normal app use.
        setSystemBarsHidden(false)
        scheduleAmbient()
    }

    /** Show/hide the system bars (the Portal back/home OSD band) immersively. */
    private fun setSystemBarsHidden(hidden: Boolean) {
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        if (hidden) {
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        permissionRequestHelper.handleRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        for (fragment in supportFragmentManager.fragments) {
            if (fragment is PlayerFragment && fragment.isVisible) {
                fragment.onUserLeaveHint()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        scheduleAmbient()
        // Launcher/setting may have changed while we were away.
        refreshImmortalNavState()
    }

    override fun onStop() {
        super.onStop()
        orientationListener.disable()
        // Don't let the timer fire (or the overlay linger) while backgrounded.
        ambientHandler.removeCallbacks(ambientRunnable)
        hideAmbient()
    }

    override fun onDestroy() {
        ambientHandler.removeCallbacks(ambientRunnable)
        unbindService(serviceConnection)
        chromecast.destroy()
        super.onDestroy()
    }

    private companion object {
        const val AMBIENT_IDLE_MS = 60_000L
    }
}
