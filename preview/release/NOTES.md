# NovaOS Home 2.0 Preview 0.4 — Navigation & Multitasking (EXPERIMENTAL)

Android17/API37, code4, package `uk.co.traynor.novaos.preview`. Same development certificate as Preview0.2/0.3: update in place without clearing preview settings. No production integration, signing key, credentials, vault access, Hidden VPN or Tor changes.

## New

- Internal Samsung-inspired Recents/Home/Back bar above Android's navigation area. Reversible button order; optional Nova gesture strip (up Home, sideways Back, hold Recents) with accessible menu alternatives. Keyboard navigation insets are consumed separately.
- Nova Recents: adaptive horizontally swiped app cards, app icon/title, retained app tabs, tap Resume, swipe up to close, accessible close buttons and confirmed profile-local Close All. Safe placeholder cards avoid capturing login/account pages. Closing cards closes live pages, not installed shortcuts or stored cookies.
- WebViews stay attached while switching between apps. Lifecycle updates occur only when state changes. Foreground selected apps remain resumed; inactive apps with public Media Session state `playing` may also remain resumed. Activity stop/profile changes still pause them. Existing audio under Nova Home remains supported where the website permits it. Screen-off/Android-level background playback remains deferred.
- Conditional mini media surface: app name, bounded website-supplied Media Session track title/state and Return to player. Playing sessions take priority over paused ones. If WebView/site exposes no public media metadata, no playback state is invented. This host has no supported interface for invoking a site's Media Session action handlers, so Play/Pause/Previous/Next controls are not fabricated; use the actual website player. No DOM media scraping, credential access, handler replacement, autoplay or stream extraction.
- Optional Fold Edge Panel: inset left/right handle, height position, up to6 profile-local favourite apps, recent apps, Store/Home/settings. Disabled by default, no system gesture exclusion or notification permissions.
- Nova event centre records real successful installations and wallpaper changes, persisted per preview profile, with delete/clear controls and a40-event bound. No fabricated download/update/task events or Android notification interception.
- Existing sharp icons, folder previews, Store search/install status, Home editing, themes and Fold layouts retained. Optional Browser Mode now persists per app/profile; App Mode remains the default.

## Where to find it

Use the bottom Nova navigation bar. In Quick Panel or Home settings choose **Navigation & multitasking** to configure controls and Edge favourites, or **Nova events** to view events. The optional Edge handle is inset24dp from the selected edge to avoid Android's gesture edge. Its availability/placement can be changed or disabled.

## Verification and limits

Host navigation/media/lifecycle policy checks, catalogue hashes and package boundaries run before builds. Focused Android17 cases cover Recents/resume/swipe close/Close All/Back fallback, retained DOM/WebViews, mini metadata availability, lifecycle ownership, settings/events persistence and profile separation, Edge/event controls, gesture alternatives, navigation insets, existing foreground audio and Fold/large-text layouts. Exact runtime outcome is appended below. Synthetic fixtures do not certify third-party playback or personal Google authentication.

The Google navigation repair from0.3 is preserved unchanged: ordinary website navigation is allowed when the provider accepts it, OAuth authorization requests require confirmed external browsing, no UA spoofing/cookie copying/provider bypass. Preview browser cookies are ordinary app-private storage shared within demo profiles, not an encrypted vault. Live pages/Recents are memory-only and may be lost if Android kills the process or renderer. No promise of native APK/PWA installability or production security approval.

Owner acceptance: update0.3; play Music, tap Nova Home/Recents, resume it, open another app and return; test Back/history and reversed/gesture controls; enable/configure Edge; install an app/change wallpaper and inspect Nova events; fold/unfold/rotate; relaunch and check settings/layouts persist. Use provider controls when the mini transport interface is unavailable.

Old GitHub Preview0.1 used a lost temporary development key; do not uninstall it if its local data is needed. Production NovaOS must remain installed.

Publication marker: preview-multitasking-code4
