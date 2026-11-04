# NovaOS Home 2.0 + Nova Store Preview

Independent Android17/API37 application: `uk.co.traynor.novaos.preview`, version `0.1-preview`/code1. Development signing only. Installs alongside production NovaOS; no shared UID, production browser/profile path, production key, vault engine, VPN, Tor, account or media permission is included. It is not secure storage or a production1.7.11 release.

## Interactive features

- Adapted recovered Home2 pager/dock/swipe-up drawer/long-press controls, live clock/date and original procedural wallpapers. Independent Primary Preview/Hidden Preview Home pages, installed apps, folders, drawer order, Dock, grid/icon preferences and wallpaper settings.
- Folders, app/menu ordering, add/remove from Home, Dock pin/unpin/reorder, page editing, Quick Panel, theme controls and explicit menu alternatives to gestures. Adaptive cover/inner/landscape grids and large text bounds.
- Built-in wallpapers and Android system photo picker. Images decoded at bounded2048px size into preview-private files; crop/reposition/zoom, blur/dim and aspect-correct preview. Imported images are not encrypted. Restore defaults affects only the selected preview profile.
- Versioned bundled catalogue:50 researched official destinations,46 verified official icon assets and4 original text fallbacks. No ratings/reviews/download counts. All50 conservatively classified Website Shortcut;0 verified PWAs/0 verified Web Apps, because installability and complete site compatibility were not verified. Classification describes website capability, not the internal launcher/container presentation.
- Store search/categories/details/confirmed install/idempotent installation/MyApps/open/uninstall/custom HTTPS destinations/Add to Home/Pin to Dock. Installation never opens a site or authenticates automatically.
- Installed entries open internally with app identity, back/forward/address/reload/stop, per-app tabs, supported JavaScript/forms and optional user-selected file uploads. Normal Home switching and configuration changes retain live WebViews. No UA spoofing, JS bridge, certificate bypass, silent external handoff or production credential import.

## Primary V1 reuse

`browser/PrimaryV1Policy.kt` ports the existing Primary V1 pure viewport/security/navigation policies from source6661507b. `PreviewBrowserModel.configure` follows `ui/PrivateBrowser.kt:secureBrowserWebView` settings: JS+DOM on, first-party cookies, third-party cookies off, native UA, mobile viewport, no forced overview,100%text zoom, single-window defaults, mixed content denied, file/content access denied and TLS errors cancelled. Production Primary V1 is not modified or replaced. Hidden V1 lifecycle/VPN components are absent.

The new app’s `PreviewApplication` selects `nova_preview_v1` WebView data directory suffix before browser initialization; the Android package sandbox independently separates all preview WebView data from production. Preview profiles/apps share this ordinary preview cookie store and are not security boundaries. Google and other providers may restrict embedded sign-in. A confirmed external-browser option is available, but external login does not automatically authenticate the internal container.

## Known limits

- Camera/microphone/geolocation web permission grants and internal downloads are unavailable; use a confirmed external browser where needed. There are no vault imports, cross-Space transfers, native APK installation or production Evidence data.
- Eight tabs maximum per app. Live DOM/tab state survives ordinary Home/rotation transitions, not application process death; installed entries/layouts persist. Dock holds five shortcuts; pinning when full replaces the earliest slot.
- Custom sites are unverified HTTPS shortcuts; manifests are not remotely trusted or inspected. Site DRM, subscriptions, embedded login and mobile compatibility remain provider dependent.
- Physical Galaxy Z Fold acceptance is performed by the owner. Emulated viewport verification is not physical hinge testing. Source compile alone is not runtime approval.

## Build and verification

Run `./gradlew -p preview :app:assembleDebug :app:assembleDebugAndroidTest`. This configures only the nested preview project and never the production build/signing tasks.

Host checks: `python3 preview/tests/check_preview.py`, then compile/run `preview/tests/PreviewRulesChecks.java` with PreviewRules.java.22 URL/adaptive ordering checks and50 catalogue/46 icon-hash/package/source-boundary checks passed. Full app and Android-test APK assembly passed locally with verified Gradle9.5.0/JDK17/SDK37.1. Signature, unique package, minSdk37, production-certificate mismatch, APK16KB zip alignment and arm64 native16KB LOAD alignment were verified.

First focused CI run41864577746 built/signed/installed the preview, then the emulator reported `INSTRUMENTATION_ABORTED: System has crashed` before test cases executed. No runtime pass is claimed from that run. The next coherent run includes lighter in-process compilation, lower emulator memory, Vulkan disabled/no snapshots and a30-second boot stabilization step; the unchanged eight-case assertion guard still rejects aborted/incomplete output. Consult the exact-source Actions artifact for current runtime results.

## Owner acceptance

Install the APK as NovaOS Preview; keep production NovaOS installed. Check Home swipes/Dock/folders, switch both demonstration profiles, select/crop wallpaper, install an app and open it internally, return Home/reopen the same app, try a supported login, rotate/fold/unfold and relaunch to confirm saved layouts. Treat all preview browsing as ordinary, non-encrypted browsing.

Second run41869208311 again passed preview compilation/package/certificate gates but failed before emulator boot: host free space5782.94MB was below the userdata reservation7372.80MB. No cases ran. The next workflow frees only unrelated tools on its ephemeral runner, requires a7GB reserve, uses4GB userdata, and bounds boot/test waits. Application source is unchanged; the Library APK SHA256 remains5c72a7d6a37312dfdd6747e12b1278e483213533e1351105af1f763ca3c29993.
