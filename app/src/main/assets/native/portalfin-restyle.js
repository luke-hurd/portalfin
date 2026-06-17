/*
 * PortalFin restyle: re-skin jellyfin-web with the Portal palette and
 * swap the Jellyfin logo/wordmark for ours.
 *
 * Re-runnable: invoked once when included via injectionScript chain AND
 * again on every onPageFinished from the native client (safety net). All
 * setup is idempotent — the global guard below skips repeat init of
 * observer/interval/history-patches.
 */
console.log('[portalfin] restyle script loaded');
if (window.__portalfinInited) {
    // Already running — just re-apply CSS + logo swap for this page.
    try {
        const ev = new Event('portalfin-reapply');
        window.dispatchEvent(ev);
    } catch (_) {}
}

// Phase 0: seed jellyfin-web's localStorage with native auth credentials
// BEFORE the web client bootstraps. If we have native creds, jellyfin-web
// will see itself as already-signed-in and route directly to /home.
try {
    if (window.PortalFinBridge && typeof window.PortalFinBridge.getCredentials === 'function') {
        const credsJson = window.PortalFinBridge.getCredentials();
        if (credsJson && credsJson !== 'null') {
            window.localStorage.setItem('jellyfin_credentials', credsJson);
            console.log('[portalfin] seeded credentials');
        } else {
            console.log('[portalfin] no native creds, skipping seed');
        }
    }
} catch (e) {
    console.warn('[portalfin] failed to seed creds', e);
}

(() => {
    const PRIMARY = '#0866FF';            // button backgrounds where contrast is fine
    const PRIMARY_HOVER = '#3D86FF';
    const PRIMARY_TEXT = '#6BA0FF';        // lighter blue for text-on-dark contexts
    const BACKGROUND = '#1A1A1A';
    const SURFACE = '#2B2B2B';
    const SURFACE_HIGH = '#363636';
    const ON_BACKGROUND = '#F0F0F0';
    const ON_SURFACE = '#DADADA';
    const PORTAL_TOP_INSET = 64;

    // Phase 1: hide page IMMEDIATELY (before first paint, while DOM is parsing).
    // This style is appended directly to <head> as soon as <head> exists.
    function injectVisibilityGate() {
        if (document.getElementById('portalfin-gate')) return;
        const gate = document.createElement('style');
        gate.id = 'portalfin-gate';
        gate.textContent = `html { visibility: hidden !important; }`;
        // Even if <head> isn't ready, documentElement always exists
        (document.head || document.documentElement).appendChild(gate);
    }

    function removeVisibilityGate() {
        const gate = document.getElementById('portalfin-gate');
        if (gate) gate.remove();
    }

    function injectStyle() {
        if (document.getElementById('portalfin-style')) return;
        const style = document.createElement('style');
        style.id = 'portalfin-style';
        style.textContent = `
            :root {
                --portalfin-primary: ${PRIMARY};
                --portalfin-bg: ${BACKGROUND};
                --portalfin-surface: ${SURFACE};
                /* The ElegantFin server theme colors the grid card "mini" play
                   button green (--btnMiniPlayColor: #299a5d). Retint it portal
                   blue at the variable source so we don't have to out-specify
                   the theme's high-specificity .layout-mobile rule. */
                --btnMiniPlayColor: ${PRIMARY} !important;
                --btnMiniPlayBorderColor: ${PRIMARY_HOVER} !important;
            }

            html, body {
                background: var(--portalfin-bg, ${BACKGROUND}) !important;
                color: ${ON_BACKGROUND};
                font-family: -apple-system, "Inter", "Helvetica Neue", Arial, sans-serif;
                transition: background-color 8s ease;
            }
            /* Preserve Material Icons font for glyph elements */
            .material-icons {
                font-family: "Material Icons" !important;
            }

            /* === HEADER GEOMETRY ===
               skinHeader: fixed at top:0, total height = 64 (Portal overlay
               reserve) + 40 (icon row) = 104px on home/no-tabs, +28 if tabs.
               Content rows sit BELOW the 64px overlay reserve. */
            .skinHeader,
            .skinHeader.semiTransparent,
            .skinHeader-withBackground {
                top: 0 !important;
                padding-top: ${PORTAL_TOP_INSET}px !important;
                padding-bottom: 0 !important;
                box-sizing: content-box !important;
                visibility: visible !important;
                transform: translateY(0) !important;
                background: ${SURFACE} !important;
            }
            /* === HIDE jellyfin-web's skinHeader entirely, EVERYWHERE including
                  the video player. We replace it with #portalfin-header (back +
                  title + cast only). jellyfin's header carries the junk controls
                  (home/favorites/search/profile/music) the user does NOT want on
                  the player. */
            .skinHeader { display: none !important; }

            /* === PORTALFIN CUSTOM HEADER ===
               Sits BELOW Portal's 64px overlay band, not under it.
               Total chrome height: 64 (Portal) + 44 (our header) = 108px,
               but our header is positioned at top:64 so its own height
               is just 44px. */
            /* Portal's WebView area starts BELOW the 64px system overlay,
               so top:0 in CSS = directly under Portal back/home buttons.
               No 64px reserve needed in our CSS. */
            #portalfin-header {
                position: fixed !important;
                top: 0 !important;
                left: 0 !important;
                right: 0 !important;
                height: 68px !important;        /* fits 48px wordmark + 7px bottom pad + breathing */
                padding: 0 12px 7px 12px !important;
                /* Frosted glass: translucent base + blur + saturation boost
                   so scrolled content is fuzzy-visible underneath. */
                background: var(--portalfin-header-bg, rgba(26, 26, 26, 0.6)) !important;
                -webkit-backdrop-filter: blur(20px) saturate(180%) !important;
                backdrop-filter: blur(20px) saturate(180%) !important;
                box-sizing: border-box !important;
                display: flex !important;
                align-items: center !important;
                justify-content: space-between !important;
                z-index: 1000 !important;
            }
            #portalfin-header .pf-left,
            #portalfin-header .pf-right {
                display: flex !important;
                align-items: center !important;
                gap: 8px !important;
            }
            #portalfin-header .pf-btn {
                width: 36px !important;
                height: 36px !important;
                border-radius: 50% !important;
                border: none !important;
                background: transparent !important;
                color: ${ON_BACKGROUND} !important;
                cursor: pointer !important;
                display: inline-flex !important;
                align-items: center !important;
                justify-content: center !important;
                padding: 0 !important;
            }
            #portalfin-header .pf-btn:hover { background: ${SURFACE} !important; }
            #portalfin-header .pf-btn svg { width: 22px !important; height: 22px !important; }
            #portalfin-header .pf-wordmark {
                width: 210px !important;        /* +20% from 175 */
                height: 48px !important;        /* +20% from 40 */
                background-image: url('/native/wordmark.png') !important;
                background-size: contain !important;
                background-repeat: no-repeat !important;
                background-position: left center !important;
                margin-left: 13px !important;   /* aligns under Portal back button */
            }
            #portalfin-header .pf-title {
                color: ${ON_BACKGROUND} !important;
                font-size: 20px !important;
                font-weight: 600 !important;
                margin-left: 8px !important;
                white-space: nowrap !important;
                overflow: hidden !important;
                text-overflow: ellipsis !important;
                max-width: 600px !important;
            }
            /* Title is empty by default — only shows when JS populates it */
            #portalfin-header .pf-title:empty { display: none !important; }
            /* class used by JS to hide back-button on home / wordmark off-home */
            #portalfin-header .pf-hidden {
                display: none !important;
            }
            /* === VIDEO PLAYER PAGE ===
               THE BLACK-VIDEO RULE — read before touching. The HTML5 <video>
               composites as a hardware SurfaceView BEHIND the WebView; the
               WebView shows it through a TRANSPARENT punch-through hole. If ANY
               page-level layer (html/body/#reactRoot/.videoPlayerContainer/.page)
               has an OPAQUE background on the video route, it paints over that
               hole → video shows for a few frames then goes black (audio + OSD
               survive). So on the video route every page layer MUST be
               transparent. The black LETTERBOX bars come from the NATIVE window
               background (set to black on the video route in WebViewFragment),
               NOT from CSS — see portalfin-video-blackscreen memory.
               DO NOT set an opaque background here. That is the exact regression
               that broke playback. */
            /* Player top bar = our minimal #portalfin-header (back + title +
               cast ONLY — search/profile were removed; jellyfin's junk header
               stays globally hidden). It MUST NOT use backdrop-filter on the
               video route: a backdrop-filter forces the WebView compositor into
               a framebuffer-readback path that blacks out the whole hardware
               video layer. Use a plain gradient scrim instead. */
            body.pf-video-page #portalfin-header {
                display: flex !important;
                background: #000 !important;
                background-color: #000 !important;
                -webkit-backdrop-filter: none !important;
                backdrop-filter: none !important;
                /* Fade in/out with the OSD instead of snapping. */
                transition: opacity 0.3s ease !important;
                opacity: 1 !important;
            }
            /* Hide the top header (back/title/cast) IN SYNC WITH THE OSD: when
               jellyfin marks the bottom OSD hidden ('.videoOsdBottom.hide' on
               idle), fade our header out too so no UI lingers during playback.
               Tapping the screen un-hides the OSD (removes .hide) → header
               returns. Chrome 131 WebView supports :has(). */
            body.pf-video-page:has(.videoOsdBottom.hide) #portalfin-header,
            body.pf-video-page:has(.videoOsdBottom-hidden) #portalfin-header {
                opacity: 0 !important;
                pointer-events: none !important;
            }
            /* Player top bar is back + title + cast ONLY (hard requirement).
               No wordmark, and no profile/settings button on the player. */
            body.pf-video-page #portalfin-header .pf-wordmark,
            body.pf-video-page #portalfin-header .pf-profile {
                display: none !important;
            }
            html.pf-video-page,
            body.pf-video-page,
            body.pf-video-page #reactRoot,
            body.pf-video-page .mainAnimatedPages,
            body.pf-video-page .skinBody,
            body.pf-video-page .backgroundContainer,
            body.pf-video-page .videoPlayerContainer,
            body.pf-video-page .page:not(.hide) {
                background: transparent !important;
                background-color: transparent !important;
            }
            /* LETTERBOX BARS = BLACK. The <video> is object-fit:contain (16:9
               video in a 16:10 viewport => ~40px bars top+bottom). Those bars are
               painted by the VIDEO ELEMENT'S OWN background — NOT the page, WebView
               or native window (all of which are behind the transparent page and
               were the wrong layer). Setting the video element background black is
               what actually makes the bars black. Verified on-device. */
            body.pf-video-page video,
            body.pf-video-page .videoPlayerContainer video,
            body.pf-video-page .htmlvideoplayer {
                background: #000 !important;
                background-color: #000 !important;
            }
            /* Transport controls 2x size (user request): the play/pause, skip,
               and rewind/forward buttons. Base icon is 26px → 52px. */
            body.pf-video-page .videoOsdBottom .btnRewind .material-icons,
            body.pf-video-page .videoOsdBottom .btnFastForward .material-icons,
            body.pf-video-page .videoOsdBottom .btnPreviousChapter .material-icons,
            body.pf-video-page .videoOsdBottom .btnNextChapter .material-icons,
            body.pf-video-page .videoOsdBottom .btnPause .material-icons,
            body.pf-video-page .videoOsdBottom .btnPlay .material-icons,
            /* Match the secondary controls (CC + settings) to the same 52px. */
            body.pf-video-page .videoOsdBottom .btnSubtitles .material-icons,
            body.pf-video-page .videoOsdBottom .btnVideoOsdSettings .material-icons {
                font-size: 52px !important;
                width: 52px !important;
                height: 52px !important;
            }
            /* Favorite (heart) uses a ::before pseudo-glyph (its .material-icons
               is zeroed) — size the ::before to 52px to match. */
            body.pf-video-page .videoOsdBottom .btnUserRating .material-icons::before {
                font-size: 52px !important;
            }
            body.pf-video-page .videoOsdBottom .btnRewind,
            body.pf-video-page .videoOsdBottom .btnFastForward,
            body.pf-video-page .videoOsdBottom .btnPreviousChapter,
            body.pf-video-page .videoOsdBottom .btnNextChapter,
            body.pf-video-page .videoOsdBottom .btnPause,
            body.pf-video-page .videoOsdBottom .btnPlay,
            body.pf-video-page .videoOsdBottom .btnSubtitles,
            body.pf-video-page .videoOsdBottom .btnVideoOsdSettings,
            body.pf-video-page .videoOsdBottom .btnUserRating {
                width: 64px !important;
                height: 64px !important;
            }
            /* Disable headroom auto-hide — keep header visible on scroll */
            .headroom--unpinned, .skinHeader.headroom--unpinned {
                transform: translateY(0) !important;
                visibility: visible !important;
            }
            /* Tighten the icon/pill row to 40px */
            .skinHeader .headerTop {
                min-height: 40px !important;
                height: 40px !important;
                padding: 0 8px !important;
                box-sizing: border-box !important;
            }
            /* Tighten the section-tab row to 28px (where present) */
            .skinHeader .headerTabs {
                min-height: 28px !important;
                height: 28px !important;
                box-sizing: border-box !important;
            }
            /* Header icon buttons: 36px square circles with proper glyphs */
            .skinHeader .paper-icon-button-light,
            .skinHeader button.paper-icon-button-light {
                width: 36px !important;
                height: 36px !important;
                min-width: 36px !important;
                min-height: 36px !important;
                padding: 0 !important;
                margin: 0 4px !important;
                background: transparent !important;
                border-radius: 50% !important;
                display: inline-flex !important;
                align-items: center !important;
                justify-content: center !important;
                flex-shrink: 0 !important;
            }
            .skinHeader .paper-icon-button-light > .material-icons,
            .skinHeader .paper-icon-button-light > .xlibIcon {
                font-size: 22px !important;
                line-height: 1 !important;
            }
            /* Home/Favorites pills inside headerTop */
            .skinHeader .emby-button.button-flat,
            .skinHeader button.headerHomeButton,
            .skinHeader .headerNavLink {
                height: 32px !important;
                min-height: 32px !important;
                padding: 0 12px !important;
                font-size: 13px !important;
                border-radius: 16px !important;
            }
            /* Section-tab pills (Movies/Suggestions/Favorites) shouldn't
               grow beyond the 28px tab row */
            .skinHeader .headerTabs .emby-tab-button {
                height: 24px !important;
                min-height: 24px !important;
                padding: 2px 12px !important;
                font-size: 12px !important;
            }

            /* Page content sits below our 44px header (Portal overlay is
               ABOVE the WebView area, not within it). */
            html body .libraryPage,
            html body .homePage,
            html body .mainAnimatedPage,
            html body .pageWithAbsoluteTabs,
            html body .itemDetailPage,
            html body div[data-role="page"] {
                padding-top: 70px !important;   /* matches 68px header + 2px breathing */
                margin-top: 0 !important;
            }

            /* Drawer (if it ever shows) also respects the inset */
            .mainDrawer-scrollContainer { padding-top: ${PORTAL_TOP_INSET}px !important; }

            /* Always show jellyfin's header chrome — even on detail/play pages
               where jellyfin-web sets the 'hiddenViewMenuBar' class.
               NOT on the video player page: there jellyfin's junk header must
               stay hidden so only our minimal back+title+cast bar shows. */
            body:not(.pf-video-page).hiddenViewMenuBar .skinHeader,
            body:not(.pf-video-page) .hiddenViewMenuBar .skinHeader {
                display: flex !important;
            }

            /* Push detail page hero down so our top reserve doesn't
               sit on top of the backdrop poster image. */
            .itemBackdrop, .detailPagePrimaryContainer {
                margin-top: 0 !important;
            }

            /* === BUTTON SYSTEM (scoped, not blanket) ===
               Three variants:
                 1. Action pills:  Sign In, Connect, Resume, Play, etc.
                                    52dp tall, 24dp radius, full Meta blue.
                 2. Icon buttons:  Back arrow, profile, search, cast, glyphs
                                    in toolbars. 44dp square, 50% radius.
                 3. Section pills: Movies/Suggestions/Favorites tabs etc.
                                    Auto height, 12dp radius, transparent. */

            /* (1) ACTION PILLS — only target buttons that have actual text
               content (not just an icon). The :not(.paper-icon-button-light)
               carve-out is the key — icon buttons no longer get the 52dp
               min-height + 24dp pill that made them look weird. */
            .button-submit,
            .raised.button-submit,
            button[is="emby-button"].raised:not(.paper-icon-button-light):not(.detailFloatingButton):not(.button-cancel):not(.subdued) {
                background-color: ${PRIMARY} !important;
                background-image: none !important;
                color: ${ON_BACKGROUND} !important;
                border-radius: 24px !important;
                min-height: 52px !important;
                font-weight: 600 !important;
                font-size: 16px !important;
                text-transform: none !important;
                box-shadow: none !important;
                letter-spacing: 0 !important;
                padding-left: 24px !important;
                padding-right: 24px !important;
            }
            .button-submit:hover,
            .raised.button-submit:hover { background-color: ${PRIMARY_HOVER} !important; }

            /* Secondary action pills (Cancel / outlined / flat) */
            .button-cancel,
            .raised.button-cancel,
            button[is="emby-button"].subdued:not(.paper-icon-button-light) {
                background-color: ${SURFACE} !important;
                color: ${ON_BACKGROUND} !important;
                border-radius: 24px !important;
                min-height: 52px !important;
                font-weight: 500 !important;
                text-transform: none !important;
                box-shadow: none !important;
                padding-left: 24px !important;
                padding-right: 24px !important;
            }

            /* (2) ICON BUTTONS — circular 44dp targets. Header icons,
               back-arrows, in-row glyph buttons. */
            button.paper-icon-button-light,
            .paper-icon-button-light {
                width: 44px !important;
                height: 44px !important;
                min-width: 44px !important;
                min-height: 44px !important;
                border-radius: 50% !important;
                padding: 0 !important;
                background-color: transparent !important;
                margin: 0 4px !important;
            }
            /* Glyphs inside icon buttons — bigger so they read at 1.5m */
            .paper-icon-button-light > .material-icons,
            .paper-icon-button-light > .xlibIcon {
                font-size: 26px !important;
            }
            /* The standalone back arrow that floats at top-left of detail/
               library pages (NOT inside skinHeader) — make it even larger. */
            .libraryPage > .paper-icon-button-light:first-child > .material-icons,
            .itemDetailPage .paper-icon-button-light[title="Back" i] > .material-icons,
            .pageHeader .paper-icon-button-light > .material-icons {
                font-size: 32px !important;
            }

            /* (3) SECTION TABS — Movies / Suggestions / Favorites pills */
            .emby-tab-button,
            .sectionTabs .emby-tab-button {
                border-radius: 12px !important;
                padding: 8px 16px !important;
                min-height: 0 !important;
                margin: 0 4px !important;
                font-weight: 500 !important;
                font-size: 14px !important;
                text-transform: none !important;
            }

            /* Text accents — use the LIGHTER blue so it reads on dark.
               Solid PRIMARY is reserved for button backgrounds. */
            a, .accent, .button-link, .button-flat:hover, .button-accent-flat,
            .inputLabelFocused, .selectLabelFocused, .textareaLabelFocused,
            .upNextDialog-countdownText, .metadataSidebarIcon,
            .emby-tab-button:hover, .emby-tab-button.show-focus:focus,
            .guide-date-tab-button.emby-tab-button-active,
            .guide-date-tab-button:focus, .buttonActive,
            .paper-icon-button-light:active:not(:disabled),
            .paper-icon-button-light.show-focus:focus,
            #dialogToc .bookplayerButtonIcon:hover,
            #dialogToc .toc li a:active,
            #dialogToc .toc li a:hover {
                color: ${PRIMARY_TEXT} !important;
            }

            /* Genre/director/studio links on detail pages: lighter still */
            .genreItems a, .listItem a, .detailLink, .textActionButton {
                color: ${PRIMARY_TEXT} !important;
                font-weight: 500;
            }

            /* Inputs — make them visible on dark surface */
            .emby-input, .emby-textarea, .emby-select,
            input.emby-input, textarea.emby-textarea, select.emby-select {
                background-color: ${SURFACE} !important;
                color: ${ON_BACKGROUND} !important;
                border: 1px solid ${SURFACE_HIGH} !important;
                border-radius: 12px !important;
                padding: 14px 16px !important;
                font-size: 16px !important;
            }
            .emby-input:focus, .emby-textarea:focus, .emby-select:focus,
            input.emby-input:focus, textarea.emby-textarea:focus, select.emby-select:focus {
                border-color: ${PRIMARY} !important;
                outline: none !important;
            }
            .inputLabel, .selectLabel, .textareaLabel,
            .inputLabelFocused {
                color: ${ON_SURFACE} !important;
            }

            /* Checkboxes — match jellyfin-web's actual selectors */
            .emby-checkbox-label,
            .checkboxLabel,
            .emby-checkbox + span { color: ${ON_BACKGROUND} !important; }

            .emby-checkbox:checked + span + .checkboxOutline,
            .emby-checkbox:focus:not(:checked) + span + .checkboxOutline,
            .emby-checkbox:focus + span + .checkboxOutline {
                border-color: ${PRIMARY} !important;
                background-color: ${PRIMARY} !important;
            }
            .checkboxOutline { border-radius: 6px !important; }

            /* Surfaces (NOT .card — those are tiles, kept transparent below) */
            .listItem, .paperListItem, .formDialog,
            .dialog, .actionSheet {
                background-color: ${SURFACE} !important;
            }
            .card-overlay, .listItemBody {
                background-color: transparent !important;
            }

            /* Toolbars / app bar / page background */
            .skinHeader,
            .pageTitle,
            .headerBackground,
            .libraryPage,
            .mainAnimatedPage,
            .background-theme-light,
            .background-theme-dark,
            #reactRoot { background-color: ${BACKGROUND} !important; }

            /* Drawer / nav */
            .mainDrawer, .mainDrawer-scrollContainer {
                background-color: ${BACKGROUND} !important;
            }
            .navMenuOption-selected,
            .navMenuOption.selected {
                background-color: ${SURFACE} !important;
                border-left-color: ${PRIMARY} !important;
                color: ${PRIMARY} !important;
            }
            .navMenuOption:hover { background-color: ${SURFACE_HIGH} !important; }

            /* Toggle switches */
            .mdl-switch.is-checked .mdl-switch__thumb { background: ${PRIMARY} !important; }
            .mdl-switch.is-checked .mdl-switch__track {
                background: rgba(8,102,255,0.4) !important;
            }

            /* Hide jellyfin's logo splash — we already showed our own */
            .pageTitleWithDefaultLogo,
            .pageTitleWithLogo { background-image: none !important; }
            .splashLogo, #appLoadingPage .splashLogo { display: none !important; }

            /* Page titles like "Sign in to continue" */
            .pageTitle, h1, h2, h3 {
                color: ${ON_BACKGROUND} !important;
                font-weight: 600 !important;
            }

            /* Dialog headers / modal titles */
            .formDialogHeader, .dialogTitle {
                background-color: ${BACKGROUND} !important;
                color: ${ON_BACKGROUND} !important;
            }

            /* Scrollbars */
            ::-webkit-scrollbar { width: 8px; height: 8px; }
            ::-webkit-scrollbar-thumb {
                background: ${SURFACE_HIGH} !important;
                border-radius: 4px;
            }
            ::-webkit-scrollbar-track { background: ${BACKGROUND} !important; }

            /* === SPA route transitions ===
               document.startViewTransition() snapshots old + new state and
               crossfades between them. Default UA animation is a 250ms
               opacity fade. We tune to a slightly snappier 180ms with an
               ease-out curve so navigations feel responsive but smooth. */
            ::view-transition-old(root),
            ::view-transition-new(root) {
                animation-duration: 180ms !important;
                animation-timing-function: cubic-bezier(0.2, 0, 0, 1) !important;
            }
            ::view-transition-old(root) {
                animation-name: pf-fade-out !important;
            }
            ::view-transition-new(root) {
                animation-name: pf-fade-in !important;
            }
            @keyframes pf-fade-out { from { opacity: 1; } to { opacity: 0; } }
            @keyframes pf-fade-in { from { opacity: 0; } to { opacity: 1; } }

            /* The portalfin header should NOT crossfade — it stays put across
               navigations. Give it a stable view-transition-name so the
               browser treats it as a continuous element. */
            #portalfin-header {
                view-transition-name: portalfin-header;
            }

            /* === AMBIENT MODE OVERLAY ===
               Fullscreen rotating gallery activated after 60s of idle.
               Two crossfading background layers, scrim for legibility,
               oversized clock, current item title. */
            #portalfin-ambient {
                position: fixed !important;
                inset: 0 !important;
                z-index: 100000 !important;
                pointer-events: none !important;
                opacity: 0 !important;
                transition: opacity 600ms ease !important;
                background: ${BACKGROUND} !important;
            }
            body.pf-ambient-active #portalfin-ambient {
                opacity: 1 !important;
                pointer-events: auto !important;
            }
            /* When the ambient screensaver is active, KILL the video player OSD
               and our top header so no player chrome lingers over the slideshow.
               The ambient overlay is full-screen (inset:0) so it already fills
               the area the OSD vacates. */
            body.pf-ambient-active .videoOsdBottom,
            body.pf-ambient-active .videoOsdTop,
            body.pf-ambient-active .skinHeader,
            body.pf-ambient-active #portalfin-header {
                display: none !important;
                opacity: 0 !important;
                visibility: hidden !important;
            }
            #portalfin-ambient .pf-ambient-img {
                position: absolute !important;
                inset: 0 !important;
                background-size: cover !important;
                background-position: center !important;
                opacity: 0;       /* NOT !important — JS toggles via class below */
                transition: opacity 1500ms ease-in-out !important;
            }
            #portalfin-ambient .pf-ambient-img.is-visible {
                opacity: 1 !important;
            }
            #portalfin-ambient .pf-ambient-scrim {
                position: absolute !important;
                inset: 0 !important;
                background: linear-gradient(
                    to bottom,
                    rgba(0,0,0,0.55) 0%,
                    rgba(0,0,0,0.15) 35%,
                    rgba(0,0,0,0.15) 65%,
                    rgba(0,0,0,0.7) 100%
                ) !important;
            }
            #portalfin-ambient .pf-ambient-clock {
                position: absolute !important;
                left: 48px !important;
                bottom: 48px !important;
                color: ${ON_BACKGROUND} !important;
                text-shadow: 0 2px 16px rgba(0,0,0,0.6) !important;
            }
            #portalfin-ambient .pf-ambient-time {
                font-size: 88px !important;
                font-weight: 200 !important;
                line-height: 1 !important;
                letter-spacing: -2px !important;
            }
            #portalfin-ambient .pf-ambient-date {
                font-size: 22px !important;
                font-weight: 400 !important;
                opacity: 0.85 !important;
                margin-top: 8px !important;
            }
            /* Two title layers (a/b) crossfade in lockstep with the backdrop
               images so the title is always synced to the artwork behind it —
               no snapping a new title onto the old backdrop. Base = invisible;
               .is-visible fades in over the same 1500ms as .pf-ambient-img. */
            #portalfin-ambient .pf-ambient-meta {
                position: absolute !important;
                right: 48px !important;
                bottom: 56px !important;
                color: ${ON_BACKGROUND} !important;
                font-size: 18px !important;
                font-weight: 500 !important;
                text-shadow: 0 2px 16px rgba(0,0,0,0.6) !important;
                max-width: 50% !important;
                text-align: right !important;
                opacity: 0;       /* NOT !important — JS toggles via .is-visible */
                transition: opacity 1500ms ease-in-out !important;
            }
            #portalfin-ambient .pf-ambient-meta.is-visible {
                opacity: 0.85 !important;
            }
            /* When the ambient item has title-art, render it as an image the same
               size it appears on the detail page (360×90), bottom-right-anchored. */
            #portalfin-ambient .pf-ambient-meta.pf-ambient-meta-logo {
                width: 360px !important;
                height: 90px !important;
                max-width: 45% !important;
                background-position: right bottom !important;
                background-size: contain !important;
                background-repeat: no-repeat !important;
                filter: drop-shadow(0 2px 16px rgba(0,0,0,0.6)) !important;
            }
            #portalfin-ambient .pf-ambient-meta.pf-ambient-meta-logo.is-visible {
                opacity: 1 !important;
            }

            /* === PROFILE / SETTINGS MENU =================================
               Viewer-only kiosk: JS prunes to 5 rows (Playback, Subtitles,
               Downloads, Select Server, Sign Out). This makes the remaining
               list feel native — generous touch rows, clear separation,
               centered column, larger icons + labels. */
            body.pf-profile-page .readOnlyContent,
            body.pf-profile-page .settingsContainer,
            body.pf-profile-page .verticalSection {
                max-width: 720px !important;
                margin-left: auto !important;
                margin-right: auto !important;
            }
            /* The row list: stack with real gaps instead of smashed-together.
               Tight vertical padding + bottom-margin on each section so two
               sections (per-user + "User") read as one continuous list with
               no big empty gap where the hidden headers used to be. */
            body.pf-profile-page .verticalSection {
                display: flex !important;
                flex-direction: column !important;
                gap: 12px !important;
                padding: 0 24px !important;
                margin: 0 0 12px 0 !important;
            }
            body.pf-profile-page .verticalSection:first-of-type {
                margin-top: 16px !important;
            }
            body.pf-profile-page .listItem {
                background: ${SURFACE} !important;
                border-radius: 16px !important;
                min-height: 64px !important;
                padding: 0 22px !important;
                margin: 0 !important;
                transition: background 160ms ease, transform 120ms ease !important;
            }
            body.pf-profile-page .listItem:hover,
            body.pf-profile-page .listItem:active {
                background: ${SURFACE_HIGH} !important;
                transform: scale(0.99) !important;
            }
            /* "Close app" escape hatch — smaller and quieter than the settings
               rows. Centered below the list, subtle outline rather than a solid
               fill so it doesn't compete with the real settings actions. */
            .pf-close-app {
                display: block !important;
                margin: 28px auto 40px !important;
                padding: 9px 26px !important;
                font-size: 14px !important;
                font-weight: 500 !important;
                color: ${ON_SURFACE} !important;
                background: transparent !important;
                border: 1px solid rgba(255,255,255,0.22) !important;
                border-radius: 12px !important;
                cursor: pointer !important;
                transition: background 160ms ease !important;
            }
            .pf-close-app:hover,
            .pf-close-app:active {
                background: ${SURFACE} !important;
            }
            /* Icon: circular Meta-blue-tinted chip, bigger glyph. */
            body.pf-profile-page .listItem .listItemIcon,
            body.pf-profile-page .listItem .material-icons {
                color: ${PRIMARY_TEXT} !important;
                font-size: 26px !important;
                width: 44px !important;
                height: 44px !important;
                line-height: 44px !important;
                margin-right: 8px !important;
                border-radius: 50% !important;
                background: rgba(8, 102, 255, 0.14) !important;
                text-align: center !important;
                flex-shrink: 0 !important;
            }
            /* Label: larger, comfortable weight. */
            body.pf-profile-page .listItem .listItemBodyText,
            body.pf-profile-page .listItem .listItemBody {
                font-size: 17px !important;
                font-weight: 500 !important;
            }
            /* Chevron at the end of each row for affordance. margin-left:auto
               pushes it to the right; margin-right gives it breathing room from
               the row edge so it isn't jammed against the side. */
            body.pf-profile-page .listItem::after {
                content: '' !important;
                margin-left: auto !important;
                margin-right: 8px !important;
                width: 9px !important;
                height: 9px !important;
                border-right: 2px solid rgba(255,255,255,0.35) !important;
                border-bottom: 2px solid rgba(255,255,255,0.35) !important;
                transform: rotate(-45deg) !important;
                flex-shrink: 0 !important;
            }
            /* Hide the server name + version footer (e.g. "ElegantFin v26.06.06").
               The ElegantFin server theme injects it as a ::after pseudo-element
               on the page's '.padded-bottom-page' container that sits below the
               settings buttons (confirmed via CDP: the matching node is
               div.padded-left.padded-right.padded-bottom-page, content
               "ElegantFin v26.06.06"). There's no DOM node to remove, so we
               suppress the pseudo. The other selectors are belt-and-suspenders
               in case a different server theme attaches it elsewhere. */
            body.pf-profile-page .padded-bottom-page::after,
            body.pf-profile-page .padded-bottom-page::before,
            body.pf-profile-page #myPreferencesMenuPage::after,
            body.pf-profile-page #myPreferencesMenuPage::before,
            body.pf-profile-page .userPreferencesPage::after,
            body.pf-profile-page .userPreferencesPage::before,
            body.pf-profile-page .readOnlyContent::after,
            body.pf-profile-page .readOnlyContent::before,
            body.pf-profile-page .verticalSection:last-child::after,
            body.pf-profile-page .settingsContainer::after,
            body.pf-profile-page .pageContainer::after {
                content: none !important;
                display: none !important;
            }
            body.pf-profile-page .appfooter,
            body.pf-profile-page .pageFooter,
            body.pf-profile-page .footer,
            body.pf-profile-page .appVersionNumber {
                display: none !important;
            }

            /* Loading spinners → use Meta blue */
            .mdl-spinner__layer-1,
            .mdl-spinner__layer-3 { border-color: ${PRIMARY} !important; }
            .mdl-spinner__layer-2,
            .mdl-spinner__layer-4 { border-color: ${PRIMARY_HOVER} !important; }
            .docspinner,
            .progressring-spiner { color: ${PRIMARY} !important; border-color: ${PRIMARY} !important; }

            /* === Comprehensive sweep: every selector that used Jellyfin teal === */
            .emby-input:focus, .emby-textarea:focus,
            .emby-select-withcolor:focus,
            .itemSelectionPanel,
            .card:focus .cardBox.visualCardBox,
            .card:focus .cardBox:not(.visualCardBox) .cardScalable {
                border-color: ${PRIMARY} !important;
            }

            .button-submit,
            .selectionCommandsPanel,
            .alphaPickerButton-tv:focus,
            .itemProgressBarForeground,
            .countIndicator,
            .fullSyncIndicator,
            .mediaSourceIndicator,
            .playedIndicator,
            .navMenuOption-selected,
            .emby-button.show-focus:focus,
            .guide-channelHeaderCell:focus,
            .programCell:focus,
            .guide-date-tab-button.show-focus:focus,
            .emby-button.detailFloatingButton {
                background-color: ${PRIMARY} !important;
                color: ${ON_BACKGROUND} !important;
            }

            .emby-select-tv-withcolor:focus {
                background-color: ${PRIMARY} !important;
                color: ${ON_BACKGROUND} !important;
                border-color: ${PRIMARY} !important;
            }

            .layout-tv .emby-button.detailFloatingButton:focus {
                background-color: ${ON_BACKGROUND} !important;
                color: ${PRIMARY} !important;
            }

            /* Override jellyfin's hardcoded backgrounds */
            html, body, .backgroundContainer, .dialog,
            .nowPlayingContextMenu, .nowPlayingPlaylist,
            .drawer-open, .mainDrawer, .ui-corner-all, .ui-shadow,
            .wizardStartForm,
            #bookPlayer, #comicsPlayer, #pdfPlayer, #dialogToc {
                background-color: ${BACKGROUND} !important;
            }
            .skinHeader-withBackground,
            .collapseContent,
            .formDialogFooter:not(.formDialogFooter-clear),
            .formDialogHeader:not(.formDialogHeader-clear),
            .paperList, .visualCardBox,
            .appfooter, .playlistSectionButton,
            .toast { background-color: ${SURFACE} !important; color: ${ON_BACKGROUND} !important; }
            .skinHeader.semiTransparent { background-color: rgba(26,26,26,0.8) !important; }

            /* Default card backgrounds (the colored library tiles when no art) */
            .defaultCardBackground1,
            .defaultCardBackground2,
            .defaultCardBackground3,
            .defaultCardBackground4,
            .defaultCardBackground5 { background-color: ${SURFACE} !important; }

            /* ListItem hover/focus → Portal style */
            .listItem:focus { background: ${SURFACE_HIGH} !important; }
            .listItem:hover { background: ${SURFACE} !important; }

            /* === KIOSK MODE: hide admin chrome we don't want === */
            /* Hamburger menu — never accessible on Portal */
            .mainDrawerButton, .headerHomeButton.headerButton[title="Menu" i],
            button.mainDrawerButton {
                display: none !important;
            }

            /* Empty rectangle artifact in the upper-left of the header on
               Home — that's the empty .pageTitle / .headerLeft text slot.
               Hide ANY empty slot that has no rendered text. */
            .pageTitle:empty,
            .headerLeft:empty {
                display: none !important;
            }
            /* Pages without a back button OR a title get a collapsed header-left */
            body:not(.libraryDocument) .headerLeft .headerHomeButton ~ .pageTitle:not(.pageTitleWithLogo),
            .skinHeader .headerLeft > .pageTitle {
                /* keep visible if has actual text but if it's empty CSS rule
                   above hides it */
            }
            /* Hide entire side drawer */
            .mainDrawer, .drawer { display: none !important; }
            /* Hide Sync Play / group session buttons */
            .headerSyncButton, .syncPlayButton, .btnSyncPlay,
            button[title="SyncPlay" i], button[title*="ync"][title*="lay"] {
                display: none !important;
            }
            /* (icon button sizing handled in the Layer-2 button system above) */
            /* Hide admin links in user dropdown menu */
            .actionSheetMenuItem[data-id="dashboard" i],
            .actionSheetMenuItem[data-id="metadata" i],
            .actionSheetMenuItem[href*="dashboard"],
            .actionSheetMenuItem[href*="metadata"] {
                display: none !important;
            }
            /* Hide entire Administration section header in settings */
            h2.userSettingsHeading-administration,
            .administrationSection { display: none !important; }
            /* Block dashboard styling from leaking */
            body.dashboardDocument { display: none !important; }

            /* === DE-CARDED TILES ===
               Strip the visual frames AROUND tile artwork — no card boxes,
               no borders, no scalable wrapper border. Don't touch the
               cardImageContainer's background-image which is the actual art. */
            .card,
            .card .cardBox,
            .card .cardScalable,
            .card .cardOverlayContainer,
            .card .cardContent,
            .card .cardOverlayButtonContainer {
                background-color: transparent !important;
                border: none !important;
                box-shadow: none !important;
            }
            /* Image itself: clean rounded corners. Don't override the
               background-image — that IS the artwork. */
            .card .cardImageContainer {
                border-radius: 8px !important;
                overflow: hidden !important;
                border: none !important;
            }
            /* Grid play button: keep it permanently visible. jellyfin shows the
               bottom-right play button only on hover (inline display:none until
               then), which on a touch device just flashed for a frame on load
               and then vanished. Force it shown + opaque so it's always there,
               with no green→blue flash (the blue tint comes from the
               --btnMiniPlayColor override above, applied from the first paint). */
            .layout-mobile .card .cardOverlayButton-br[data-action="play"],
            .card .cardOverlayButton-br[data-action="play"] {
                display: inline-flex !important;
                opacity: 1 !important;
                transition: none !important;
            }
            .card .cardImage {
                background-size: cover !important;
                background-position: center !important;
            }
            /* Tighter inter-tile spacing (default cardBox margin is .6em) */
            .card .cardBox {
                margin: 4px !important;
                padding: 0 !important;
            }
            /* Hover/focus: subtle lift, no thick border */
            .card.show-focus:not(.show-animation) .cardBox.visualCardBox,
            .card.show-focus:not(.show-animation) .cardBox:not(.visualCardBox) .cardScalable {
                border: none !important;
                outline: 2px solid ${PRIMARY_TEXT} !important;
                border-radius: 8px !important;
            }
            /* Card title labels — readable white */
            .cardText, .cardText-first, .cardText a {
                color: ${ON_BACKGROUND} !important;
                font-weight: 500 !important;
            }
            .cardText-secondary {
                color: ${ON_SURFACE} !important;
                opacity: 0.65;
            }
            /* Section heading above tile rows */
            .sectionTitle, .sectionTitleContainer .sectionTitle,
            .verticalSection h2 {
                font-size: 16px !important;
                font-weight: 600 !important;
                color: ${ON_BACKGROUND} !important;
                margin-bottom: 4px !important;
            }
            /* Tighter row padding */
            .verticalSection {
                margin-bottom: 16px !important;
            }

            /* === DETAIL PAGE GLOW-UP ===================================== */

            /* (1) Play / Resume / Replay buttons: Meta blue capsule (NOT
                   a circle — these have text labels, they need to be pill-shaped) */
            .itemDetailPage .btnPlay,
            .itemDetailPage .btnReplay {
                background-color: ${PRIMARY} !important;
                background-image: none !important;
                color: ${ON_BACKGROUND} !important;
                width: auto !important;          /* override the 52px from the icon-button rule */
                height: 44px !important;
                min-width: 0 !important;
                border-radius: 22px !important;  /* capsule, not circle */
                padding: 0 22px !important;
                margin: 0 8px !important;
            }
            .itemDetailPage .btnPlay .material-icons,
            .itemDetailPage .btnPlay span,
            .itemDetailPage .btnReplay .material-icons,
            .itemDetailPage .btnReplay span {
                color: ${ON_BACKGROUND} !important;
            }

            /* (1b) Trim the action row to: Play / Watched / Favorite / Download.
                    Hide trailer (no YouTube), shuffle, instant mix, split, more menu,
                    timer cancel buttons (those are admin functions). */
            .itemDetailPage .btnPlayTrailer,
            .itemDetailPage .btnInstantMix,
            .itemDetailPage .btnShuffle,
            .itemDetailPage .btnSplitVersions,
            .itemDetailPage .btnCancelSeriesTimer,
            .itemDetailPage .btnCancelTimer,
            .itemDetailPage .btnMoreCommands {
                display: none !important;
            }
            /* Force the Download button visible (jellyfin sometimes adds 'hide' class). */
            .itemDetailPage .btnDownload {
                display: inline-flex !important;
            }

            /* (2) Hero artwork: tall, anchored to the TOP so faces/heads
                  are visible (not legs). The gradient fades the bottom
                  half so the title block (pulled up with negative margin
                  below) sits ON the artwork as it fades to background. */
            /* Do NOT override 'top' here. jellyfin-web positions the backdrop
               relative to the .itemDetailPage page, which carries
               padding-top: 70px to clear the Portal system overlay + our
               header. Forcing top:0 yanked the backdrop (and the title block
               overlaying it) up under the Portal back/home icons. Keep native
               positioning; just size it and anchor the artwork to the top. */
            .itemDetailPage .itemBackdrop,
            .detailPagePrimaryContainer .itemBackdrop {
                /* Pull the artwork UP behind the frosted #portalfin-header so the
                   header's backdrop-filter has the poster to blur through (it
                   was sitting on flat background = a dull dark strip). We use a
                   NEGATIVE MARGIN, not a 'top' override (see note above): the
                   page's padding-top:70px reserve is untouched, so the title
                   block / buttons don't move. Height grows by the same 70px so
                   the backdrop's BOTTOM stays at 550px and the wrapper's
                   -160px overlay still lands in the same place. */
                margin-top: -70px !important;
                height: 550px !important;
                min-height: 550px !important;
                background-position: center top !important;
                background-size: cover !important;
            }
            /* Some jellyfin layouts apply the bg image to a child rather than
               the .itemBackdrop element itself — handle both. */
            .itemDetailPage .itemBackdrop > div[style*="background-image"],
            .itemDetailPage .backdropImage,
            .detailPagePrimaryContainer .backdropImage {
                background-position: center top !important;
                background-size: cover !important;
            }
            /* Bottom gradient: starts higher up and runs deeper so the
               title block (which now overlays the backdrop) reads cleanly
               against the artwork. */
            .itemDetailPage .itemBackdrop::after,
            .detailPagePrimaryContainer .itemBackdrop::after {
                content: '' !important;
                position: absolute !important;
                left: 0 !important;
                right: 0 !important;
                bottom: 0 !important;
                height: 55% !important;
                background: linear-gradient(
                    to bottom,
                    rgba(26,26,26,0) 0%,
                    rgba(26,26,26,0.55) 45%,
                    rgba(26,26,26,0.85) 75%,
                    var(--portalfin-bg, ${BACKGROUND}) 100%
                ) !important;
                pointer-events: none !important;
            }
            /* (2b) THE LAYOUT FIX: pull the title/year/runtime/Play-button
                    block UP so it overlays the bottom of the backdrop.
                    Probe confirmed structure:
                      .itemDetailPage
                        ├── .itemBackdrop
                        ├── .detailLogo            (movie logo, sometimes empty)
                        └── .detailPageWrapperContainer  ← title/meta/play/synopsis live in here
                    Pulling the wrapper up by 240px puts the .nameContainer
                    on the lower third of the backdrop, behind the gradient. */
            .itemDetailPage .detailPageWrapperContainer,
            .detailPagePrimaryContainer + .detailPageWrapperContainer {
                position: relative !important;
                z-index: 2 !important;
                margin-top: -160px !important;
                background: transparent !important;
            }
            /* The detailLogo (movie title-art image) is rendered absolute-positioned
               inside .itemDetailPage with 'top: 333px' set inline by jellyfin-web,
               which puts it below the backdrop and off-screen below the wrapper.
               Override to anchor it bottom-left of the backdrop. The .itemDetailPage
               is itself position:absolute, so the logo's 'top' value is what we
               control. Backdrop is 480px tall starting at top:0 of the page,
               so top:380px puts the logo's top edge 100px above backdrop bottom. */
            .itemDetailPage .detailLogo {
                position: absolute !important;
                top: 380px !important;       /* renders at viewport y≈290; bottom ≈380, ~10px above info row at y=390 */
                left: 0 !important;          /* left:0 + right:0 + margin auto = horizontally centered */
                right: 0 !important;
                bottom: auto !important;
                margin-left: auto !important;
                margin-right: auto !important;
                width: 360px !important;
                max-width: 45% !important;
                height: 90px !important;
                background-position: center bottom !important;  /* center image within the box */
                background-size: contain !important;
                background-repeat: no-repeat !important;
                z-index: 3 !important;
            }
            /* The name/title row: bigger, with a shadow so it reads against
               varied artwork. */
            .itemDetailPage .nameContainer,
            .itemDetailPage .detailPagePrimaryContainer .nameContainer {
                position: relative !important;
                z-index: 2 !important;
                padding: 0 24px !important;
            }
            /* Hide the duplicated text title — the .detailLogo image above
               serves as the page title. (If a particular item lacks logo
               art, fall back rule below shows the text.) */
            .itemDetailPage .nameContainer h1,
            .itemDetailPage .nameContainer .itemName,
            .itemDetailPage h1.itemName,
            .itemDetailPage h1.parentName {
                display: none !important;
            }
            /* Year / runtime / RT score / "ends at" pills sit on the artwork too. */
            .itemDetailPage .itemMiscInfo,
            .itemDetailPage .mediaInfoItems {
                position: relative !important;
                z-index: 2 !important;
                text-shadow: 0 1px 8px rgba(0,0,0,0.7) !important;
                padding: 0 24px !important;
            }
            /* The action row (Play + secondary buttons) — also overlay. */
            .itemDetailPage .mainDetailButtons,
            .itemDetailPage .detailButtons,
            .nameContainer + div:has(.btnPlay),
            .nameContainer ~ div:has(.btnPlay) {
                position: relative !important;
                z-index: 2 !important;
                padding: 12px 24px 0 24px !important;
            }

            /* (3) Action row icon buttons: 52dp circles for the secondary
                  actions (Watched, Favorite, Download). EXPLICITLY excludes
                  .btnPlay and .btnReplay — those have text labels and need
                  to be capsules; their dedicated rule is below this one. */
            .itemDetailPage .detailButton:not(.btnPlay):not(.btnReplay),
            .itemDetailPage .paper-icon-button-light,
            .nameContainer + div .paper-icon-button-light {
                width: 52px !important;
                height: 52px !important;
                border-radius: 50% !important;
                margin: 0 8px !important;
                background: ${SURFACE} !important;
                transition: background 200ms ease !important;
            }
            .itemDetailPage .detailButton:hover,
            .itemDetailPage .paper-icon-button-light:hover {
                background: ${SURFACE_HIGH} !important;
            }
            .itemDetailPage .detailButton .material-icons,
            .itemDetailPage .paper-icon-button-light .material-icons {
                font-size: 26px !important;
                color: ${ON_BACKGROUND} !important;
            }
            /* Icons that should stay colored when toggled (favorite filled, etc.) */
            .itemDetailPage .ratingbutton-icon-withrating,
            .itemDetailPage .playstatebutton-icon-played {
                color: ${PRIMARY_TEXT} !important;
            }

            /* Favorite (heart) button state feedback. The server theme renders
               the filled "favorite" glyph in ALL states, so tapping toggled the
               favorite server-side but gave no visual change — looked broken.
               Drive the glyph off the button's title: "Add to favorites" =>
               NOT favorited => outline heart; otherwise => filled + accent.
               We zero the original ligature text and paint our own via ::before
               (Material Icons is a ligature font; ::before inherits it). */
            .btnUserRating .material-icons {
                font-size: 0 !important;   /* hide the always-"favorite" ligature */
            }
            .btnUserRating .material-icons::before {
                font-size: 26px !important;
                line-height: 1 !important;
            }
            /* Not favorited (title says "Add to favorites") → outline heart */
            .btnUserRating[title*="Add to favorite" i] .material-icons::before {
                content: "favorite_border" !important;
                color: ${ON_BACKGROUND} !important;
            }
            /* Favorited (any other title, e.g. "Remove from favorites") → filled */
            .btnUserRating:not([title*="Add to favorite" i]) .material-icons::before {
                content: "favorite" !important;
                color: #FF4D6D !important;
            }

            /* (4) Hide noisy technical metadata. The "1080p H264 SDR" /
                  "Dolby Digital 5.1" / "Off" rows are <select> dropdowns
                  for choosing video/audio/subtitle track. We hide the
                  entire selector section because (a) most viewers don't
                  care, (b) jellyfin defaults to the right track anyway. */
            .itemDetailPage .selectVideo,
            .itemDetailPage .selectAudio,
            .itemDetailPage .selectSubtitles,
            .itemDetailPage .detailTrackSelect,
            .itemDetailPage .selectVideoContainer,
            .itemDetailPage .selectAudioContainer,
            .itemDetailPage .selectSubtitlesContainer,
            /* their fieldset-style wrappers */
            .itemDetailPage .selectContainer-inline:has(.selectVideo),
            .itemDetailPage .selectContainer-inline:has(.selectAudio),
            .itemDetailPage .selectContainer-inline:has(.selectSubtitles) {
                display: none !important;
            }

            /* (5) Bigger synopsis for living-room readability */
            .itemDetailPage .overview,
            .itemDetailPage .detailPageOverview,
            .itemDetailPage .overview-readabletext,
            .itemDetailPage p[data-overview] {
                font-size: 18px !important;
                line-height: 1.55 !important;
                max-width: 70ch !important;
                color: ${ON_BACKGROUND} !important;
                opacity: 0.92 !important;
            }
            /* Tagline above synopsis ("How do I loathe thee?…") */
            .itemDetailPage .tagline {
                font-size: 17px !important;
                font-style: italic !important;
                color: ${ON_SURFACE} !important;
                margin-bottom: 16px !important;
            }

            /* (7) Cast as a horizontal scroll carousel with bigger avatars */
            .itemDetailPage .castContent,
            .itemDetailPage .peopleSection .itemsContainer {
                display: flex !important;
                overflow-x: auto !important;
                overflow-y: hidden !important;
                gap: 16px !important;
                padding: 8px 0 !important;
                white-space: nowrap !important;
                scrollbar-width: none !important;
            }
            .itemDetailPage .castContent::-webkit-scrollbar,
            .itemDetailPage .peopleSection .itemsContainer::-webkit-scrollbar {
                display: none !important;
            }
            .itemDetailPage .card.personCard,
            .itemDetailPage .peopleSection .card {
                flex: 0 0 auto !important;
                width: 140px !important;
            }
            .itemDetailPage .card.personCard .cardImageContainer,
            .itemDetailPage .peopleSection .cardImageContainer {
                border-radius: 50% !important;
                width: 110px !important;
                height: 110px !important;
                margin: 0 auto !important;
            }
            .itemDetailPage .card.personCard .cardText,
            .itemDetailPage .peopleSection .cardText {
                text-align: center !important;
                font-size: 14px !important;
                white-space: normal !important;
            }

            /* (8) Similar movies row — bigger and more prominent */
            .itemDetailPage .similarSection .sectionTitle,
            .itemDetailPage .nextUpSection .sectionTitle {
                font-size: 22px !important;
                font-weight: 700 !important;
                margin-bottom: 12px !important;
                margin-top: 32px !important;
            }
            .itemDetailPage .similarSection .card,
            .itemDetailPage .nextUpSection .card {
                width: 180px !important;
            }

            /* (extra) Strip remaining card containers app-wide. We already
               un-carded home tiles; do the same for any leftover surface
               panels (formDialogs, paperLists in detail/settings, etc.). */
            .itemDetailPage .detailSection,
            .itemDetailPage .verticalSection,
            .itemDetailPage .listItemMetadata,
            .itemDetailPage .listItem-border,
            .listItem-border,
            .collapseContent,
            .paperList,
            .visualCardBox,
            .formDialogFooter:not(.formDialogFooter-clear),
            .formDialogHeader:not(.formDialogHeader-clear) {
                background-color: transparent !important;
                border: none !important;
                box-shadow: none !important;
            }
        `;
        (document.head || document.documentElement).appendChild(style);
    }

    function replaceLogos() {
        document.querySelectorAll('img').forEach((img) => {
            const src = img.getAttribute('src') || '';
            if (
                /(banner-?light\.png|logoblack\.png|logowhite\.png|jellyfin\.svg)/i.test(src) ||
                (/jellyfin/i.test(src) && /logo|banner|wordmark/i.test(src))
            ) {
                img.style.display = 'none';
            }
        });
    }

    let signaled = false;
    function notifyApplied() {
        if (signaled) return;
        signaled = true;
        try {
            if (window.PortalFinBridge && typeof window.PortalFinBridge.onRestyleApplied === 'function') {
                window.PortalFinBridge.onRestyleApplied();
                console.log('[portalfin] notified bridge');
            }
        } catch (e) {
            console.warn('[portalfin] bridge not available', e);
        }
    }

    // Profile/settings menu (#/mypreferencesmenu): jellyfin-web renders ~13
    // .listItem rows distinguished only by text (no class/href hooks). For a
    // viewer-only kiosk we keep just the 5 that matter and hide the section
    // headers, leaving a single clean list.
    const PROFILE_KEEP = ['playback', 'subtitles', 'select server', 'sign out'];
    function pruneProfileMenu() {
        const onProfile = /mypreferences|preferencesmenu/i.test(window.location.hash || '');
        document.body.classList.toggle('pf-profile-page', onProfile);
        if (!onProfile) return;
        const items = document.querySelectorAll('.listItem');
        if (items.length === 0) return;
        items.forEach((el) => {
            const txt = (el.textContent || '').trim().replace(/\s+/g, ' ').toLowerCase();
            const keep = PROFILE_KEEP.some((k) => txt.startsWith(k));
            el.style.display = keep ? '' : 'none';
        });
        // Hide the per-user / "User" section headers — the trimmed list reads
        // fine without them.
        document.querySelectorAll('.verticalSection > h2, .sectionTitle, .verticalSection .sectionTitle')
            .forEach((h) => { h.style.display = 'none'; });
        // Collapse any .verticalSection that has no visible rows left after the
        // prune (otherwise its padding leaves a big empty gap mid-list).
        document.querySelectorAll('.verticalSection').forEach((sec) => {
            const visibleRows = Array.from(sec.querySelectorAll('.listItem'))
                .filter((r) => r.style.display !== 'none');
            sec.style.display = visibleRows.length ? '' : 'none';
        });
        // Hide the server name + version footer (e.g. "ElegantFin v26.06.06").
        // jellyfin-web's class for it is inconsistent, so match on content:
        // any small leaf element whose text looks like a "<name> vX.Y.Z" stamp.
        document.querySelectorAll('.appfooter, .pageFooter, .footer, .appVersionNumber').forEach((el) => {
            el.style.display = 'none';
        });
        // Belt-and-suspenders: also hide any small leaf element that looks like
        // a version stamp, in case a theme renders it as a real node instead.
        const verRe = /v\d+\.\d+/i;
        document.querySelectorAll('div, p, span, small').forEach((el) => {
            if (el.children.length === 0) {
                const t = (el.textContent || '').trim();
                if (t.length < 40 && verRe.test(t)) el.style.display = 'none';
            }
        });
        addCloseAppButton();
    }

    // A small "Close app" button pinned at the bottom of the profile page.
    // Fully terminates the app (via the native bridge) for a cold restart.
    // Deliberately smaller/quieter than the settings rows — it's an escape
    // hatch, not a primary action.
    function addCloseAppButton() {
        if (document.querySelector('.pf-close-app')) return;
        // Anchor it after the last visible settings section so it sits below
        // the buttons rather than floating mid-page.
        const sections = [...document.querySelectorAll('.verticalSection')]
            .filter((s) => s.style.display !== 'none');
        const anchor = sections[sections.length - 1];
        if (!anchor) return;
        const btn = document.createElement('button');
        btn.className = 'pf-close-app';
        btn.type = 'button';
        btn.textContent = 'Close app';
        btn.addEventListener('click', () => {
            try {
                if (window.PortalFinBridge && window.PortalFinBridge.closeApp) {
                    window.PortalFinBridge.closeApp();
                }
            } catch (_) {}
        });
        anchor.insertAdjacentElement('afterend', btn);
    }

    function applyAll(reason) {
        console.log('[portalfin] applyAll:', reason);
        injectStyle();
        replaceLogos();
        kioskizeChrome();
        buildCustomHeader();
        updateWordmark();
        applyTimeOfDayTheme();
        pruneProfileMenu();
        // jellyfin-web re-renders the menu async after route change; re-prune.
        if (/mypreferences|preferencesmenu/i.test(window.location.hash || '')) {
            setTimeout(pruneProfileMenu, 300);
            setTimeout(pruneProfileMenu, 900);
        }
        // (diagnostic probes removed)
        // Now that <style> is in place, lift the visibility gate.
        // Use rAF so the style has applied before paint.
        requestAnimationFrame(() => {
            removeVisibilityGate();
            requestAnimationFrame(notifyApplied);
        });
    }

    /**
     * Kiosk mode: hide jellyfin-web admin/dashboard chrome we don't want.
     * PortalFin is a viewer-only experience; admins should manage via web.
     */
    /**
     * Build a custom PortalFin header that REPLACES jellyfin-web's
     * .skinHeader entirely. Keeps the design simple and predictable.
     */
    function buildCustomHeader() {
        if (document.getElementById('portalfin-header')) return;
        const hdr = document.createElement('div');
        hdr.id = 'portalfin-header';
        hdr.innerHTML = `
            <div class="pf-left">
                <button class="pf-btn pf-back" aria-label="Back">
                    <svg viewBox="0 0 24 24" width="22" height="22" fill="${ON_BACKGROUND}"><path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"/></svg>
                </button>
                <div class="pf-wordmark"></div>
                <div class="pf-title"></div>
            </div>
            <div class="pf-right">
                <button class="pf-btn pf-cast" aria-label="Cast">
                    <svg viewBox="0 0 24 24" width="22" height="22" fill="${ON_BACKGROUND}"><path d="M21 3H3c-1.1 0-2 .9-2 2v3h2V5h18v14h-7v2h7c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM1 18v3h3c0-1.66-1.34-3-3-3zm0-4v2c2.76 0 5 2.24 5 5h2c0-3.87-3.13-7-7-7zm0-4v2c4.97 0 9 4.03 9 9h2c0-6.08-4.93-11-11-11z"/></svg>
                </button>
                <button class="pf-btn pf-profile" aria-label="Settings">
                    <svg viewBox="0 0 24 24" width="22" height="22" fill="${ON_BACKGROUND}"><path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg>
                </button>
            </div>
        `;
        // Wire up actions
        // Back button: history.back() is unreliable coming out of the
        // preferences/settings/downloads area — jellyfin-web's SPA router
        // doesn't always re-render the destination, leaving the page blank.
        // For those routes, navigate explicitly to home so the router actually
        // loads + paints it. Everywhere else, normal back.
        hdr.querySelector('.pf-back').addEventListener('click', () => {
            const h = (window.location.hash || '').toLowerCase();
            const fromSettingsArea = /mypreferences|preferencesmenu|settings|downloads|userprofile|playback|subtitles|controls|display|quickconnect|selectserver/.test(h);
            if (fromSettingsArea) {
                // Just setting the hash makes jellyfin-web "restore" a cached
                // (often empty) home view instead of rebuilding it — that's the
                // dead homescreen. Force a full reload of the home route so the
                // view is built fresh. The restyle re-injects on page load.
                window.location.replace(window.location.pathname + '#/home.html');
                window.location.reload();
            } else {
                history.back();
            }
        });
        hdr.querySelector('.pf-cast').addEventListener('click', (e) => {
            // jellyfin-web's cast button logic: trigger their existing button if present
            const existingCast = document.querySelector('.headerCastButton, .castButton');
            if (existingCast) existingCast.click();
            // jellyfin-web anchors the "Play On" actionSheet to whatever button it
            // thinks was clicked. Our real cast button is hidden (at the origin),
            // so the sheet pops up in the top-LEFT corner by the logo. Reposition
            // it under OUR visible cast button (top-right). The sheet renders
            // async, so poll briefly until it appears.
            const btn = e.currentTarget;
            let tries = 0;
            const place = () => {
                const sheet = document.querySelector('.dialog.actionsheet-not-fullscr, .actionSheet');
                if (sheet) {
                    const br = btn.getBoundingClientRect();
                    // Anchor the sheet's RIGHT edge to the button's right edge and
                    // let it grow leftward. We must NOT compute from the sheet's
                    // measured width: jellyfin plays a scaleup animation, so an
                    // early measurement reads a half-width and the sheet then
                    // animates off the right edge. Anchoring 'right' is immune to
                    // that — width can change freely without shifting the anchor.
                    sheet.style.left = 'auto';
                    sheet.style.right = Math.round(window.innerWidth - br.right) + 'px';
                    sheet.style.top = Math.round(br.bottom + 6) + 'px';
                    return;
                }
                if (tries++ < 20) setTimeout(place, 25);
            };
            place();
        });
        // Profile/settings: jellyfin-web's preferences menu (Sign Out, Select
        // Server, Playback, Subtitles). pruneProfileMenu() trims it to the
        // kiosk subset once the route loads.
        hdr.querySelector('.pf-profile').addEventListener('click', () => {
            window.location.hash = '#/mypreferencesmenu';
        });
        document.body.appendChild(hdr);
    }

    /**
     * Toggle the .pf-video-page flag on <html> and <body> based on whether the
     * HTML5 video player is actually mounted.
     *
     * IMPORTANT — why DOM detection, not the URL hash: on this jellyfin-web
     * build the player opens as an OVERLAY without changing location.hash (it
     * stays on '#/details...'). The old hash test (/^#\/video/) therefore never
     * matched.
     *
     * SIGNAL = jellyfin-web's OWN '.transparentDocument' class on <html>.
     * jellyfin adds it to <html> exactly while the video player is foregrounded
     * (verified via devtools during real playback: html class list contained
     * 'transparentDocument', and the .videoPlayerContainer was display:flex with
     * a visible <video>). It removes it when the player closes. This is far more
     * reliable than '.videoPlayerContainer-onTop' (a transient state that is NOT
     * present during steady-state playback — keying on it left pf-video-page
     * false, so the opaque body bg painted over the video = black screen) or
     * '.videoPlayerContainer' presence (it persists after exit = stuck black).
     * The MutationObserver re-runs this when the class flips; no route event.
     */
    function updateVideoPage() {
        const playing = document.documentElement.classList.contains('transparentDocument') ||
            (() => {
                const c = document.querySelector('.videoPlayerContainer');
                const v = c && c.querySelector('video');
                return !!(c && v && getComputedStyle(c).display !== 'none');
            })();
        document.body.classList.toggle('pf-video-page', playing);
        document.documentElement.classList.toggle('pf-video-page', playing);
    }

    /**
     * Show/hide the wordmark on home only. Populate the page title
     * (next to the back arrow) on non-home pages.
     */
    function updateWordmark() {
        const wm = document.querySelector('#portalfin-header .pf-wordmark');
        const bb = document.querySelector('#portalfin-header .pf-back');
        const tt = document.querySelector('#portalfin-header .pf-title');
        if (!wm) return;
        const hash = window.location.hash || '';
        const isHome = hash === '' || hash === '#' || hash === '#/' ||
                       /^#!?\/?(home(\.html)?|index)/i.test(hash);
        // Flag the video-player state — drives the .pf-video-page CSS (hide our
        // header's backdrop-filter so video isn't black; black letterbox; let
        // jellyfin's own header own the exit/Back button). See updateVideoPage.
        updateVideoPage();
        // Use class so we beat the !important rules in our stylesheet
        wm.classList.toggle('pf-hidden', !isHome);
        if (bb) bb.classList.toggle('pf-hidden', isHome);

        // Page title — only on non-home pages.
        if (tt) setPageTitle(tt, hash, isHome);
    }

    // Token to invalidate a pending title-observer when the route changes again.
    let titleToken = 0;

    /**
     * Set the header title for the current route.
     *
     * Library pages (Movies, TV Shows…) have a title we know immediately from
     * the URL. Detail pages don't: jellyfin-web populates the item name
     * asynchronously AFTER the route changes, so the OLD title ("Movies") used
     * to linger ~1s and then visibly swap to the item name — a clunky glitch.
     *
     * Fix: for detail pages, CLEAR the title right away (better blank than
     * wrong), then watch the DOM and fill it in the instant the item name
     * appears — no fixed-delay retries, no stale carryover.
     */
    function setPageTitle(tt, hash, isHome) {
        const myToken = ++titleToken; // invalidates any earlier observer
        if (isHome) { tt.textContent = ''; return; }

        const isDetail = /^#\/details/i.test(hash);
        const immediate = derivePageTitle(hash);

        if (!isDetail) {
            // Known synchronously from the URL (Movies, TV Shows, etc.).
            tt.textContent = immediate;
            return;
        }

        // Detail page: clear the stale parent title immediately, then resolve
        // the real item name as soon as it's in the DOM.
        tt.textContent = '';
        const tryResolve = () => {
            if (myToken !== titleToken) return true; // route changed; stop
            const name = document.querySelector('h1.itemName, h2.itemName, h3.itemName, .itemName');
            const txt = name && (name.textContent || '').trim();
            if (txt && txt.length < 100 && !/Lukes-iMac/i.test(txt)) {
                tt.textContent = txt;
                return true;
            }
            return false;
        };
        if (tryResolve()) return;
        const obs = new MutationObserver(() => {
            if (myToken !== titleToken || tryResolve()) obs.disconnect();
        });
        obs.observe(document.body, { childList: true, subtree: true });
        // Safety net: stop observing after 5s regardless.
        setTimeout(() => obs.disconnect(), 5000);
    }

    /**
     * Derive a human-readable page title for the back arrow's right side.
     * Tries (in order): jellyfin-web's .pageTitle text, the URL collectionType
     * or path segment, document.title fallback.
     */
    function derivePageTitle(hash) {
        // Detail pages: try a list of selectors that jellyfin-web has used
        // for the item title in different versions/layouts.
        if (/^#\/details/i.test(hash)) {
            const candidates = [
                'h1.itemName',
                'h2.itemName',
                'h3.itemName',
                '.itemName',
                '.parentName + .itemName',
                '.titleNameLink',
                '.detailPagePrimaryContainer .nameContainer h3',
                '.detailPagePrimaryContainer h3',
                '.pageTitle',
            ];
            for (const sel of candidates) {
                const el = document.querySelector(sel);
                if (el) {
                    const txt = (el.textContent || '').trim();
                    if (txt && txt.length < 100 && !/Lukes-iMac/i.test(txt)) return txt;
                }
            }
            // Fallback: document.title (jellyfin sets it to "ItemName | Jellyfin")
            const dt = (document.title || '').replace(/\s*\|\s*Jellyfin.*$/i, '').trim();
            if (dt && !/^Jellyfin$/i.test(dt)) return dt;
            return 'Details';
        }
        // Library pages: derive from URL collectionType / path segment
        const m = hash.match(/^#\/(movies|tvshows|music|livetv|playlists|favorites|search)/i);
        if (m) {
            const map = {
                movies: 'Movies', tvshows: 'TV Shows', music: 'Music',
                livetv: 'Live TV', playlists: 'Playlists',
                favorites: 'Favorites', search: 'Search',
            };
            return map[m[1].toLowerCase()] || '';
        }
        // List pages — try the URL type=Movie param to label
        const lm = hash.match(/^#\/list[^?]*\?[^#]*[?&]type=(\w+)/i);
        if (lm) {
            const t = lm[1].toLowerCase();
            return t === 'series' ? 'TV Shows' : t === 'movie' ? 'Movies' : 'Library';
        }
        if (/^#\/list/i.test(hash)) return 'Library';
        // Fallback: jellyfin's .pageTitle text (server name etc.)
        const native = document.querySelector('.pageTitle');
        if (native) {
            const txt = (native.textContent || '').trim();
            if (txt && txt.length < 80) return txt;
        }
        return '';
    }

    /**
     * Time-of-day theme: subtly shifts the page background tint based on
     * the local hour. Cool morning -> neutral day -> warm evening ->
     * deep night. Updates the CSS custom properties that html/body and
     * the header use, so the shift cascades automatically.
     */
    function applyTimeOfDayTheme() {
        const h = new Date().getHours();
        let bg, surf, hdr;
        if (h >= 5 && h < 9)        { bg = '#1A1F22'; surf = '#2B3036'; hdr = 'rgba(26,31,34,0.55)'; }   // morning, cool
        else if (h >= 9 && h < 17)  { bg = '#1A1A1A'; surf = '#2B2B2B'; hdr = 'rgba(26,26,26,0.55)'; }   // day, neutral
        else if (h >= 17 && h < 21) { bg = '#221A1A'; surf = '#352B2B'; hdr = 'rgba(34,26,26,0.55)'; }   // evening, warm
        else                        { bg = '#0E0E12'; surf = '#1F1F26'; hdr = 'rgba(14,14,18,0.6)'; }    // night, deep
        document.documentElement.style.setProperty('--portalfin-bg', bg);
        document.documentElement.style.setProperty('--portalfin-surface', surf);
        document.documentElement.style.setProperty('--portalfin-header-bg', hdr);
    }
    if (!window.__portalfinThemeTickerStarted) {
        window.__portalfinThemeTickerStarted = true;
        // Re-tick every minute so the tint shifts as the day progresses
        setInterval(applyTimeOfDayTheme, 60_000);
    }

    function kioskizeChrome() {
        try {
            // Hamburger drawer toggle (left of header) — hide so users can't
            // open the side drawer with admin shortcuts.
            document.querySelectorAll(
                '.mainDrawerButton, button[is="paper-icon-button-light"][title="Menu"]'
            ).forEach((b) => { b.style.display = 'none'; });

            // Group / Sync Play (we don't need group sessions on kiosk)
            document.querySelectorAll(
                'button[title*="ync" i], button[title*="roup" i]'
            ).forEach((b) => { b.style.display = 'none'; });

            // Kill the entire side drawer if it's rendered
            document.querySelectorAll('.mainDrawer, .drawer').forEach((d) => {
                d.style.display = 'none';
            });

            // Force-redirect any admin/dashboard hash routes back to /home.
            // Anchor on the path part ONLY (before the query string) so that
            // detail-page URLs like #/details?id=...&serverId=... aren't
            // matched on the substring 'server' inside their query.
            const fullHash = window.location.hash || '';
            const pathPart = fullHash.split('?')[0]; // e.g. "#/dashboard" or "#/details"
            const adminRoutes = /^#\/(dashboard|configurationpage|metadataNfo|metadataimages|library|users|plugins|scheduledtasks|activity|encoder|streaming|networking|notifications|server)(\.html)?\/?$/i;
            if (adminRoutes.test(pathPart)) {
                console.log('[portalfin] redirecting from admin route', pathPart);
                window.location.hash = '#/home';
                window.location.reload();
            }

            // Hide Administration section in user settings (DOM-walk fallback
            // since ElegantFin's section heading classes are unpredictable).
            document.querySelectorAll('h2, h3, .sectionTitle, .listHeader').forEach((el) => {
                const txt = (el.textContent || '').trim().toLowerCase();
                if (txt === 'administration' || txt === 'admin') {
                    // Hide the header AND every following sibling until the
                    // next section header
                    el.style.display = 'none';
                    let next = el.nextElementSibling;
                    while (next) {
                        const tag = next.tagName.toLowerCase();
                        if (tag === 'h2' || tag === 'h3' ||
                            next.classList.contains('sectionTitle') ||
                            next.classList.contains('listHeader')) break;
                        next.style.display = 'none';
                        next = next.nextElementSibling;
                    }
                }
            });
            // Hide the per-item "three dots" context menu (Add to favorites,
            // Edit metadata, Refresh, etc.) on list rows AND card overlays.
            // These expose admin/library-management actions we don't want in a
            // viewer-only kiosk. jellyfin-web marks them data-action="menu";
            // also cover known class names. The download/play buttons use
            // different data-actions, so they're unaffected.
            document.querySelectorAll(
                '[data-action="menu"], ' +
                '.btnCardOptions, .cardOverlayButton-br, .listViewMoreButton, ' +
                '.listItemButton[data-action="menu"], .paper-icon-button-light[data-action="menu"]'
            ).forEach((b) => { b.style.display = 'none'; });
        } catch (e) {
            console.warn('[portalfin] kioskizeChrome failed', e);
        }
    }

    // Phase 1: hide everything immediately — but ONLY on the first load.
    // The script gets re-injected via onPageFinished on every nav; gating
    // on every re-inject would flash the page invisible on each SPA route
    // change. Per-window guard so re-injections are no-ops for this gate.
    if (!window.__portalfinGateRanOnce) {
        window.__portalfinGateRanOnce = true;
        injectVisibilityGate();
    }

    // Phase 2: apply restyle as soon as DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => applyAll('domcontentloaded'));
    } else {
        applyAll('immediate');
    }

    // Phase 3: re-apply on SPA navigation. jellyfin-web uses pushState — patch it.
    // Wrap in document.startViewTransition (where supported) so route changes
    // crossfade instead of snap. Portal's Chromium WebView is recent enough
    // to support same-document view transitions.
    const supportsViewTransition = typeof document.startViewTransition === 'function';
    // Guard against overlapping transitions. jellyfin-web fires a STORM of
    // history events on back-navigation; stacking startViewTransition calls
    // freezes the live DOM under a ::view-transition snapshot while jellyfin's
    // router swaps content asynchronously underneath, which can leave the page
    // stuck blank (header survives because it's position:fixed). When a
    // transition is already running, just run the work directly.
    let _transitionInFlight = false;
    function withTransition(work) {
        if (supportsViewTransition && !_transitionInFlight) {
            try {
                _transitionInFlight = true;
                const t = document.startViewTransition(work);
                t.finished.finally(() => { _transitionInFlight = false; });
                return;
            } catch (_) {
                _transitionInFlight = false;
            }
        }
        work();
    }
    const _pushState = history.pushState;
    const _replaceState = history.replaceState;
    history.pushState = function () {
        const args = arguments;
        const self = this;
        withTransition(() => {
            _pushState.apply(self, args);
            applyAll('pushState');
            updateWordmark();
        });
    };
    history.replaceState = function () {
        const args = arguments;
        const self = this;
        // replaceState is often used for state cleanup, not nav — don't transition
        _replaceState.apply(self, args);
        applyAll('replaceState');
        updateWordmark();
    };
    // Back-navigation fires a burst of popstate events and jellyfin swaps
    // content async — wrapping these in a view transition is what stranded the
    // page blank. Re-apply styles directly (no transition) so the home content
    // is never frozen under a snapshot.
    window.addEventListener('popstate', () => {
        applyAll('popstate');
        updateWordmark();
    });

    // Bail if already initialized — avoids double observers and
    // duplicate setIntervals on subsequent injections.
    if (window.__portalfinInited) return;
    window.__portalfinInited = true;

    // Allow safety-net re-injections to trigger style re-apply.
    window.addEventListener('portalfin-reapply', () => applyAll('reapply'));

    // Phase 4: re-run logo swap + admin chrome hiding on DOM mutations
    let kioskTimer = 0;
    const observer = new MutationObserver(() => {
        replaceLogos();
        // The video player mounts/unmounts WITHOUT a route change on this
        // jellyfin-web build, so the only reliable trigger is the DOM mutation.
        // Cheap (single querySelector), so run it every time, uncoalesced.
        updateVideoPage();
        // Coalesce repeated calls within 250ms — DOM-walk for Administration
        // is O(n) so we don't want to run on every keystroke.
        clearTimeout(kioskTimer);
        kioskTimer = setTimeout(kioskizeChrome, 250);
    });
    observer.observe(document.documentElement, { childList: true, subtree: true });

    // Phase 5: detect jellyfin-web sign-out so we can clear native creds.
    // jellyfin-web's logout() clears AccessToken from `jellyfin_credentials`.
    // When that flips from set → unset, notify native so MainActivity routes
    // back to LoginFragment.
    let lastHasToken = false;
    function checkSignedOut() {
        try {
            const raw = window.localStorage.getItem('jellyfin_credentials');
            const parsed = raw ? JSON.parse(raw) : null;
            const tok = parsed && parsed.Servers && parsed.Servers[0] && parsed.Servers[0].AccessToken;
            const hasToken = !!tok;
            if (lastHasToken && !hasToken) {
                console.log('[portalfin] detected sign-out');
                if (window.PortalFinBridge && typeof window.PortalFinBridge.onSignedOut === 'function') {
                    window.PortalFinBridge.onSignedOut();
                }
            }
            lastHasToken = hasToken;
        } catch (_) { /* swallow */ }
    }
    setInterval(checkSignedOut, 1000);
    checkSignedOut(); // seed lastHasToken

    // ===========================================================
    // Phase 6: Ambient slideshow at idle
    // ===========================================================
    // Portal is a stationary device that's on all the time. After 60s of
    // no user interaction, fade in a fullscreen rotating gallery of
    // backdrop art from the user's Jellyfin library, with time overlay.
    // Tap anywhere to dismiss.
    const AMBIENT_IDLE_MS = 60_000;     // 60s idle before ambient kicks in
    const AMBIENT_ROTATE_MS = 12_000;   // each backdrop visible 12s

    let ambientItems = [];
    let ambientTimer = null;
    let ambientRotateTimer = null;
    let ambientIndex = 0;

    function buildAmbientOverlay() {
        if (document.getElementById('portalfin-ambient')) return;
        const overlay = document.createElement('div');
        overlay.id = 'portalfin-ambient';
        overlay.innerHTML = `
            <div class="pf-ambient-img" id="pf-ambient-img-a"></div>
            <div class="pf-ambient-img" id="pf-ambient-img-b"></div>
            <div class="pf-ambient-scrim"></div>
            <div class="pf-ambient-clock">
                <div class="pf-ambient-time" id="pf-ambient-time">--:--</div>
                <div class="pf-ambient-date" id="pf-ambient-date"></div>
            </div>
            <div class="pf-ambient-meta" id="pf-ambient-meta-a"></div>
            <div class="pf-ambient-meta" id="pf-ambient-meta-b"></div>
        `;
        // Tap anywhere on the overlay to dismiss
        overlay.addEventListener('click', exitAmbient, { capture: true });
        overlay.addEventListener('touchstart', exitAmbient, { capture: true });
        document.body.appendChild(overlay);
    }

    function tickClock() {
        const now = new Date();
        const h = now.getHours();
        const m = now.getMinutes();
        const ampm = h >= 12 ? 'pm' : 'am';
        const h12 = ((h + 11) % 12) + 1;
        const timeEl = document.getElementById('pf-ambient-time');
        const dateEl = document.getElementById('pf-ambient-date');
        if (timeEl) timeEl.textContent = h12 + ':' + (m < 10 ? '0' + m : m) + ' ' + ampm;
        if (dateEl) {
            const days = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
            const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
            dateEl.textContent = days[now.getDay()] + ', ' + months[now.getMonth()] + ' ' + now.getDate();
        }
    }

    async function fetchAmbientItems() {
        try {
            const credsRaw = window.localStorage.getItem('jellyfin_credentials');
            if (!credsRaw) return [];
            const creds = JSON.parse(credsRaw);
            const server = creds.Servers && creds.Servers[0];
            if (!server || !server.AccessToken) return [];
            const base = server.ManualAddress || server.LocalAddress;
            const userId = server.UserId;
            const url = base + '/Users/' + userId + '/Items?Recursive=true' +
                '&IncludeItemTypes=Movie,Series' +
                '&Fields=BackdropImageTags,ImageTags' +
                '&Filters=IsNotFolder' +
                '&SortBy=Random&Limit=24';
            const resp = await fetch(url, {
                headers: { 'X-MediaBrowser-Token': server.AccessToken },
            });
            if (!resp.ok) return [];
            const data = await resp.json();
            return (data.Items || [])
                .filter(it => it.BackdropImageTags && it.BackdropImageTags.length > 0)
                .map(it => ({
                    id: it.Id,
                    name: it.Name,
                    year: it.ProductionYear,
                    url: base + '/Items/' + it.Id + '/Images/Backdrop/0?tag=' + it.BackdropImageTags[0] + '&maxWidth=1280',
                    // Title-art logo (same image used on the detail page). Present only
                    // when the item has Logo art uploaded; falls back to text otherwise.
                    logoUrl: (it.ImageTags && it.ImageTags.Logo)
                        ? base + '/Items/' + it.Id + '/Images/Logo?tag=' + it.ImageTags.Logo + '&maxHeight=180'
                        : null,
                }));
        } catch (e) {
            console.warn('[portalfin] ambient fetch failed', e);
            return [];
        }
    }

    let activeImgEl = null; // 'a' or 'b' — which element currently has the visible image
    function showNextBackdrop() {
        if (ambientItems.length === 0) return;
        const item = ambientItems[ambientIndex % ambientItems.length];
        ambientIndex++;
        // First call: show on 'a'. Subsequent: alternate.
        const target = activeImgEl === 'a' ? 'b' : 'a';
        const targetEl = document.getElementById('pf-ambient-img-' + target);
        const previousEl = activeImgEl ? document.getElementById('pf-ambient-img-' + activeImgEl) : null;
        if (!targetEl) return;
        // The title layer (a/b) pairs with the same-letter image layer so the
        // two crossfade together. Prep the OUTGOING title text/logo on the
        // target layer now (while it's still invisible), then reveal it in
        // lockstep with the backdrop inside onload — never snap a new title
        // onto the old artwork.
        const targetMeta = document.getElementById('pf-ambient-meta-' + target);
        const previousMeta = activeImgEl ? document.getElementById('pf-ambient-meta-' + activeImgEl) : null;
        if (targetMeta) {
            if (item.logoUrl) {
                // Movie's title-art logo (same image as the detail page,
                // sized to match: 360×90, bottom-right of the overlay).
                targetMeta.textContent = '';
                targetMeta.style.backgroundImage = 'url("' + item.logoUrl + '")';
                targetMeta.classList.add('pf-ambient-meta-logo');
            } else {
                // No logo art — fall back to text title + year.
                targetMeta.style.backgroundImage = '';
                targetMeta.classList.remove('pf-ambient-meta-logo');
                targetMeta.textContent = item.name + (item.year ? '  ·  ' + item.year : '');
            }
        }
        // Preload the image so the crossfade doesn't flash a blank gap
        const preload = new Image();
        preload.onload = () => {
            console.log('[portalfin] ambient img loaded', item.name);
            targetEl.style.backgroundImage = 'url("' + item.url + '")';
            // Class-based toggle so CSS .is-visible (opacity wins) over the
            // base (opacity:0) rule. Image + its title fade in together.
            targetEl.classList.add('is-visible');
            if (targetMeta) targetMeta.classList.add('is-visible');
            if (previousEl) previousEl.classList.remove('is-visible');
            if (previousMeta) previousMeta.classList.remove('is-visible');
        };
        preload.onerror = (e) => { console.warn('[portalfin] ambient image FAILED', item.url); };
        preload.src = item.url;
        activeImgEl = target;
    }

    async function enterAmbient() {
        if (document.body.classList.contains('pf-ambient-active')) return;
        // Don't go ambient while the app is backgrounded. The idle timer can
        // fire while portalfin is in the background (the WebView keeps running),
        // which made the slideshow activate behind the user's back — they'd
        // return to find the screensaver up. Only the foregrounded app screens.
        if (document.hidden) return;
        // Don't go ambient on login screens / pre-auth pages
        const credsRaw = window.localStorage.getItem('jellyfin_credentials');
        if (!credsRaw) return;
        try {
            const creds = JSON.parse(credsRaw);
            if (!(creds.Servers && creds.Servers[0] && creds.Servers[0].AccessToken)) return;
        } catch (_) { return; }
        // Don't go ambient during playback
        if (document.querySelector('video:not([paused])')) return;

        if (ambientItems.length === 0) {
            ambientItems = await fetchAmbientItems();
            if (ambientItems.length === 0) {
                console.log('[portalfin] ambient: no items, skipping');
                return;
            }
        }
        buildAmbientOverlay();
        document.body.classList.add('pf-ambient-active');
        // Ask the native side to keep the Portal screen awake while the
        // slideshow runs (otherwise Portal dims us mid-rotation).
        try {
            if (window.PortalFinBridge && window.PortalFinBridge.setAmbientActive) {
                window.PortalFinBridge.setAmbientActive(true);
            }
        } catch (_) {}
        ambientIndex = 0;
        tickClock();
        showNextBackdrop();
        ambientRotateTimer = setInterval(() => {
            tickClock();
            showNextBackdrop();
        }, AMBIENT_ROTATE_MS);
    }

    function exitAmbient() {
        if (!document.body.classList.contains('pf-ambient-active')) return;
        document.body.classList.remove('pf-ambient-active');
        if (ambientRotateTimer) {
            clearInterval(ambientRotateTimer);
            ambientRotateTimer = null;
        }
        // Release the keep-screen-on flag
        try {
            if (window.PortalFinBridge && window.PortalFinBridge.setAmbientActive) {
                window.PortalFinBridge.setAmbientActive(false);
            }
        } catch (_) {}
        // Reset idle timer so we don't immediately re-enter
        resetAmbientIdle();
    }

    function resetAmbientIdle() {
        if (ambientTimer) clearTimeout(ambientTimer);
        ambientTimer = setTimeout(enterAmbient, AMBIENT_IDLE_MS);
    }

    ['mousemove', 'pointermove', 'touchstart', 'keydown', 'wheel', 'click'].forEach(evt => {
        window.addEventListener(evt, () => {
            if (document.body.classList.contains('pf-ambient-active')) {
                exitAmbient();
            } else {
                resetAmbientIdle();
            }
        }, { passive: true });
    });
    // When the app is backgrounded, cancel the pending idle timer so ambient
    // can't trigger while hidden. When it comes back to the foreground, restart
    // the countdown from zero (and drop out of ambient if it somehow got in).
    document.addEventListener('visibilitychange', () => {
        if (document.hidden) {
            if (ambientTimer) clearTimeout(ambientTimer);
        } else {
            if (document.body.classList.contains('pf-ambient-active')) exitAmbient();
            else resetAmbientIdle();
        }
    });
    resetAmbientIdle();

    // NOTE: a "blank-content watchdog" that auto-reloaded any content route it
    // judged empty was tried here and REMOVED — it matched the #/video player
    // route, mis-detected the playing video as "blank", and reloaded mid-play,
    // bouncing the user back to home on every web-player launch (and flashing
    // card play-buttons away). The dead-home-from-settings case is handled
    // directly by the back button's explicit home reload instead. Do not
    // reintroduce a blanket reload watchdog.
})();
